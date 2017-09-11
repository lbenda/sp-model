package cz.lbenda.spmodel;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Struct;
import java.sql.Time;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.JavaInterfaceSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.machinezoo.noexception.Exceptions;

import cz.lbenda.spmodel.annotation.StoredProcedure;
import cz.lbenda.spmodel.annotation.StoredProcedureParam;

/**
 * Class which generate java class model from database stored procedure
 * @author lbenda on 9/9/17.
 */
public class GenerateSPModel {

  private static final Logger LOG = LoggerFactory.getLogger(GenerateSPModel.class);

  private static final int COLUMN_TYPE_IN = 1;
  private static final int COLUMN_TYPE_OUT = 4;
  private static final int COLUMN_TYPE_INOUT = 2;

  private File resultDir;
  private String basePackage;
  private String catalogPattern;
  private String schemaPattern;
  private String namePattern;
  private Map<String, String> catalogToSubPackage = new HashMap<>();
  private Map<String, String> schemaToSubPackage = new HashMap<>();
  private Map<String, String> namePatternToSubPackage = new HashMap<>();

  private Class sqlTypeToJava(int sqlType, boolean nullable) throws SQLException {
    switch (sqlType) {
    case Types.CHAR:
    case Types.VARCHAR:
    case Types.LONGNVARCHAR:
    case Types.NCHAR:
    case Types.NVARCHAR:
    case Types.LONGVARCHAR: return String.class;
    case Types.DECIMAL:
    case Types.NUMERIC: return BigDecimal.class;
    case Types.BOOLEAN:
    case Types.BIT: return nullable ? Boolean.class : Boolean.TYPE;
    case Types.SMALLINT:
    case Types.TINYINT:
    case Types.INTEGER: return nullable ? Integer.class : Integer.TYPE;
    case Types.BIGINT: return nullable ? Long.class : Long.TYPE;
    case Types.FLOAT:
    case Types.REAL: return nullable ? Float.class : Float.TYPE;
    case Types.DOUBLE: return nullable ? Double.class : Double.TYPE;
    case Types.BINARY:
    case Types.VARBINARY:
    case Types.LONGVARBINARY: return byte[].class;
    case Types.DATE: return Date.class;
    case Types.TIME: return Time.class;
    case Types.TIMESTAMP: return Timestamp.class;
    case Types.NCLOB:
    case Types.CLOB: return Clob.class;
    case Types.BLOB: return Blob.class;
    case Types.ARRAY: return Array.class;
    case Types.STRUCT: return Struct.class;
    case Types.REF: return Ref.class;
    }
    throw new SQLException("sqlTypeToJava ins't specified for sql type: " + sqlType);
  }

  private String columnSize15(String column) { // FIXME lbenda odstranit
    if (column.length() >= 20) {
      return column.substring(0, 20);
    }
    return column + "                    ".substring(column.length(), 20);
  }

  /** return list of stored procedures in db connection
   * @param con database connection
   * @return list of names of procedure
   */
  @NotNull
  List<ProcedureDescription> spList(@NotNull Connection con) throws SQLException {
    DatabaseMetaData meta = con.getMetaData();
    try (ResultSet rs = meta.getProcedures(catalogPattern, schemaPattern, null)) {
      List<ProcedureDescription> result = new ArrayList<>();
      while (rs.next()) {
        ProcedureDescription pd = new ProcedureDescription();
        pd.setCatalag(rs.getString("PROCEDURE_CAT"));
        pd.setSchema(rs.getString("PROCEDURE_SCHEM"));
        pd.setName(rs.getString("PROCEDURE_NAME"));
        result.add(pd);

        ResultSet rsp = meta.getProcedureColumns(pd.getCatalag(), pd.getSchema(), pd.getName(), namePattern);
        ResultSetMetaData srmd = rsp.getMetaData();
        StringBuilder sb = new StringBuilder("| "); // FIXME lbenda odstranit
        for (int i = 1; i <= srmd.getColumnCount(); i++) {
          sb.append(columnSize15(srmd.getColumnName(i))).append(" | ");
        }
        LOG.debug(sb.toString());

        while (rsp.next()) {
          StringBuilder sb1 = new StringBuilder("| "); // FIXME lbenda odstranit
          for (int i = 1; i <= srmd.getColumnCount(); i++) {
            sb1.append(columnSize15(String.valueOf(rsp.getString(i)))).append(" | ");
          }
          LOG.debug(sb1.toString());

          ParamDescription param = new ParamDescription();
          param.setName(rsp.getString("COLUMN_NAME"));
          param.setLength(rsp.getInt("LENGTH"));
          param.setScale(rsp.getInt("SCALE"));
          param.setPrecision(rsp.getInt("PRECISION"));
          param.setSqlType(rsp.getInt("DATA_TYPE"));
          param.setRadix(rsp.getInt("RADIX"));
          param.setNullable(rsp.getBoolean("NULLABLE"));
          param.setIn(rsp.getInt("COLUMN_TYPE") == COLUMN_TYPE_IN || rsp.getInt("COLUMN_TYPE") == COLUMN_TYPE_INOUT);
          param.setOut(rsp.getInt("COLUMN_TYPE") == COLUMN_TYPE_OUT || rsp.getInt("COLUMN_TYPE") == COLUMN_TYPE_INOUT);
          param.setJavaClass(sqlTypeToJava(rsp.getInt("DATA_TYPE"), param.isNullable()));
          param.setOrder(rsp.getInt("ORDINAL_POSITION"));
          pd.getParams().add(param);
        }
      }
      return result;
    }
  }

  private String underscoreToCamelCase(String procName) {
    return Arrays.stream(procName.split("-"))
        .map(str -> str.split("_"))
        .flatMap(Arrays::stream)
        .map(str ->  String.valueOf(str.charAt(0)).toUpperCase() + str.substring(1, str.length()).toLowerCase())
        .collect(Collectors.joining());
  }

  private String packageName(ProcedureDescription pd) {
    String catPac = getCatalogToSubPackage().get(pd.getCatalag());
    String schPac = getSchemaToSubPackage().get(pd.getSchema());
    String namPac = getNamePatternToSubPackage().entrySet().stream()
        .filter(entry -> Pattern.compile(entry.getKey())
        .matcher(pd.getName()).matches())
        .map(Map.Entry::getValue).findFirst().orElse(null);
    return (basePackage == null ? "" : basePackage) + (catPac == null ? "" : "." + catPac) + (schPac == null ? "" : "." + schPac)
        + (namPac == null ? "" : "." + namPac);
  }

  private String paramToGet(String paramName, Class type) {
    return (Boolean.TYPE.equals(type) ? "is" : "get") + underscoreToCamelCase(paramName);
  }

  private String paramToSet(String paramName) {
    return "set" + underscoreToCamelCase(paramName);
  }

  /**
   * Generate from procedure description java source
   * @param proc procedure
   * @return java source
   */
  @NotNull
  JavaInterfaceSource procToJava(@NotNull ProcedureDescription proc) {
    final JavaInterfaceSource result = Roaster.create(JavaInterfaceSource.class);
    result.setPackage(packageName(proc));
    result.setName(underscoreToCamelCase(proc.getName()));
    AnnotationSource<JavaInterfaceSource> annSp = result.addAnnotation(StoredProcedure.class);
    if (proc.getCatalag() != null) {
      annSp.setStringValue("catalog", proc.getCatalag());
    }
    if (proc.getSchema() != null) {
      annSp.setStringValue("schema", proc.getSchema());
    }
    annSp.setStringValue("name", proc.getName());

    proc.getParams().forEach(param -> {
      if (param.isIn()) {
        MethodSource<JavaInterfaceSource> mhs = result.addMethod();
        mhs.setName(paramToSet(param.getName()));
        ParameterSource<JavaInterfaceSource> pa = mhs.addParameter(param.getJavaClass(), "value");
        AnnotationSource<JavaInterfaceSource> annSpp = mhs.addAnnotation(StoredProcedureParam.class);
        annSpp.setStringValue("name", param.getName());
        annSpp.setLiteralValue("order", String.valueOf(param.getOrder()));
        annSpp.setLiteralValue("sqlType", String.valueOf(param.getSqlType()));
        if (param.getLength() > 0) {
          AnnotationSource<JavaInterfaceSource> annSize = pa.addAnnotation(Size.class);
          annSize.setLiteralValue("max", String.valueOf(param.getLength()));
        }
        if (param.getPrecision() > 0) {
          AnnotationSource<JavaInterfaceSource> annDigits = pa.addAnnotation(Digits.class);
          annDigits.setLiteralValue("integer", String.valueOf(param.getPrecision() - param.getScale()));
          annDigits.setLiteralValue("fraction", String.valueOf(param.getScale()));
        }
        if (!param.isNullable() && !param.getJavaClass().isPrimitive()) {
          pa.addAnnotation(NotNull.class);
        }
      }
      if (param.isOut()) {
        MethodSource<JavaInterfaceSource> mhs = result.addMethod();
        mhs.setName(paramToGet(param.getName(), param.getJavaClass()));
        mhs.setReturnType(param.getJavaClass());
        AnnotationSource<JavaInterfaceSource> annSpp = mhs.addAnnotation(StoredProcedureParam.class);
        annSpp.setStringValue("name", param.getName());
        annSpp.setLiteralValue("order", String.valueOf(param.getOrder()));
        annSpp.setLiteralValue("sqlType", String.valueOf(param.getSqlType()));
        if (!param.isNullable() && !param.getJavaClass().isPrimitive()) {
          mhs.addAnnotation(NotNull.class);
        }
      }
    });

    return result;
  }

  /** Generate procedures interfaces for given connection
   * @param con dbConnection
   * @throws SQLException problem with reading procedures from database
   * @throws IOException problem with write generate interafaces to directory
   */
  public void generate(@NotNull Connection con) throws SQLException, IOException {
    spList(con).stream().forEach(Exceptions.sneak().consumer(pd -> {
      File pack = new File(resultDir, packageName(pd).replaceAll("\\.", File.separator));
      if (!pack.exists()) {
        pack.mkdirs();
      }
      try (FileWriter fw = new FileWriter(new File(pack, underscoreToCamelCase(pd.getName()) + ".java"))) {
        fw.write(procToJava(pd).toString());
      }
    }));
  }

  public Map<String, String> getCatalogToSubPackage() {
    return catalogToSubPackage;
  }

  public void setCatalogToSubPackage(Map<String, String> catalogToSubPackage) {
    this.catalogToSubPackage = catalogToSubPackage;
  }

  public Map<String, String> getSchemaToSubPackage() {
    return schemaToSubPackage;
  }

  public void setSchemaToSubPackage(Map<String, String> schemaToSubPackage) {
    this.schemaToSubPackage = schemaToSubPackage;
  }

  public Map<String, String> getNamePatternToSubPackage() {
    return namePatternToSubPackage;
  }

  public void setNamePatternToSubPackage(Map<String, String> namePatternToSubPackage) {
    this.namePatternToSubPackage = namePatternToSubPackage;
  }

  public String getBasePackage() {
    return basePackage;
  }

  public void setBasePackage(String basePackage) {
    this.basePackage = basePackage;
  }

  public File getResultDir() {
    return resultDir;
  }

  public void setResultDir(File resultDir) {
    this.resultDir = resultDir;
  }

  public String getCatalogPattern() {
    return catalogPattern;
  }

  public void setCatalogPattern(String catalogPattern) {
    this.catalogPattern = catalogPattern;
  }

  public String getSchemaPattern() {
    return schemaPattern;
  }

  public void setSchemaPattern(String schemaPattern) {
    this.schemaPattern = schemaPattern;
  }

  public String getNamePattern() {
    return namePattern;
  }

  public void setNamePattern(String namePattern) {
    this.namePattern = namePattern;
  }
}
