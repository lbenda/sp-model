package cz.lbenda.spmodel;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author lbenda on 9/9/17.
 */
class GenerateSPModelTest {

  private final static Logger LOG = LoggerFactory.getLogger(GenerateSPModelTest.class);

  private static final String DB_URL = "jdbc:derby:memory:testDB;create=true";

  private GenerateSPModel generateSPModel;

  @BeforeEach
  void setUp() {
    generateSPModel = new GenerateSPModel();
    generateSPModel.setBasePackage("cz.lbenda.spmodel.test");
    generateSPModel.getSchemaToSubPackage().put("APP", "app");
    generateSPModel.getNamePatternToSubPackage().put("bal_.*", "balicky");
    generateSPModel.setCatalogPattern(null);
    generateSPModel.setSchemaPattern("APP");
  }

  private Connection createConnection() {
    try {
      Class.forName("org.apache.derby.jdbc.EmbeddedDriver").newInstance();
      return DriverManager.getConnection(DB_URL);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void createSP(Connection con, ProcedureDescription procedure) throws SQLException {
    String paramSql = procedure.getParams().stream().map(this::paramToSQL).collect(Collectors.joining(", "));
    String sp = String.format("CREATE PROCEDURE %s (%s) "
            + "PARAMETER STYLE JAVA READS SQL DATA LANGUAGE JAVA EXTERNAL NAME 'com.acme.sales.calculateRevenueByMonth'",
            procedure.getName(), paramSql);
    LOG.debug("Procedure SQL: " + sp);
    try (Statement stm = con.createStatement()) {
      stm.execute(sp);
    }
  }

  private String paramToSQL(ParamDescription param) {
    String inOut = param.isIn() && param.isOut() ? "INOUT" : param.isIn() ? "IN" : "OUT";
    String type = "";
    switch (param.getSqlType()) {
    case Types.VARCHAR : type = String.format("VARCHAR(%s)", param.getLength()); break;
    case Types.CHAR : type = String.format("CHAR(%s)", param.getLength()); break;
    case Types.INTEGER : type = "INTEGER"; break;
    case Types.SMALLINT : type = "SMALLINT"; break;
    case Types.BIGINT : type = "BIGINT"; break;
    case Types.REAL : type = "REAL"; break;
    case Types.DOUBLE : type = "DOUBLE PRECISION"; break;
    case Types.FLOAT : type = "FLOAT"; break;
    case Types.DECIMAL : type = String.format("DECIMAL(%s, %s)", param.getPrecision(), param.getScale()) ; break;
    case Types.NUMERIC : type = String.format("NUMERIC(%s, %s)", param.getPrecision(), param.getScale()); break;
    }
    return String.format("%s %s %s", inOut, param.getName(), type);
  }

  private ProcedureDescription newProcedure(String catalog, String schema, String name) {
    ProcedureDescription result = new ProcedureDescription();
    result.setCatalag(catalog);
    result.setSchema(schema);
    result.setName(name);
    return result;
  }

  private ParamDescription newParam(ProcedureDescription procedure, String name, int sqlType, Class javaClass, boolean in, boolean out) {
    ParamDescription result = new ParamDescription();
    result.setProcedure(procedure);
    result.setName(name);
    result.setSqlType(sqlType);
    result.setJavaClass(javaClass);
    result.setIn(in);
    result.setOut(out);
    if (procedure != null) {
      procedure.getParams().add(result);
    }
    return result;
  }

  @Test
  void spListTest() throws SQLException {
    try (Connection con = createConnection()) {
      ProcedureDescription procedure1 = newProcedure(null, null, "procedure1");
      newParam(procedure1, "param1", Types.INTEGER, Integer.class, true, false);
      newParam(procedure1, "param2", Types.FLOAT, Float.class, true, false);
      newParam(procedure1, "param3", Types.FLOAT, Float.class, true, true);
      newParam(procedure1, "param4", Types.SMALLINT, Integer.class, true, true);
      newParam(procedure1, "param5", Types.VARCHAR, String.class, false, true).setLength(10);
      newParam(procedure1, "param6", Types.CHAR, String.class, false, true).setLength(10);
      ParamDescription ppd = newParam(procedure1, "param7", Types.DECIMAL, BigDecimal.class, false, true);
      ppd.setPrecision(5);
      ppd.setScale(2);

      ProcedureDescription procedure2 = newProcedure(null, null, "procedure2");
      createSP(con, procedure1);
      createSP(con, procedure2);
      List<ProcedureDescription> procs = generateSPModel.spList(con);
      assertEquals(2, procs.size(), "Procedure names: " + Arrays.toString(procs.toArray()));
      ProcedureDescription pd = procs.get(0);
      assertTrue(pd.getParams().get(0).isIn());
      assertFalse(pd.getParams().get(0).isOut());
      assertTrue(pd.getParams().get(3).isIn());
      assertTrue(pd.getParams().get(3).isOut());
      assertFalse(pd.getParams().get(5).isIn());
      assertTrue(pd.getParams().get(5).isOut());

      assertEquals(7, pd.getParams().size());
      assertEquals(Integer.class, pd.getParams().get(0).getJavaClass());
      assertEquals(Double.class, pd.getParams().get(1).getJavaClass());
      assertEquals(Double.class, pd.getParams().get(2).getJavaClass());
      assertEquals(Integer.class, pd.getParams().get(3).getJavaClass());
      assertEquals(String.class, pd.getParams().get(4).getJavaClass());
      assertEquals(String.class, pd.getParams().get(5).getJavaClass());
    }
  }

  @Test
  void procToJavaTest() {
    ProcedureDescription procedure = newProcedure(null, null, "bal_procedure_p_name");
    ParamDescription pd = newParam(procedure, "s_param_name", Types.INTEGER, Integer.class, true, false);
    pd.setNullable(true);
    pd.setOrder(1);
    newParam(procedure, "param_name_2", Types.FLOAT, Float.TYPE, true, false).setOrder(2);
    pd = newParam(procedure, "param_name_3", Types.FLOAT, Float.class, true, true);
    pd.setNullable(true);
    pd.setOrder(3);
    newParam(procedure, "param-name-4", Types.BOOLEAN, Boolean.TYPE, true, true).setOrder(4);
    pd = newParam(procedure, "s", Types.CHAR, String.class, true, true);
    pd.setNullable(false);
    pd.setLength(10);
    pd.setOrder(5);

    pd = newParam(procedure, "s", Types.DECIMAL, BigDecimal.class, true, true);
    pd.setNullable(false);
    pd.setPrecision(5);
    pd.setScale(2);
    pd.setOrder(5);

    LOG.debug(generateSPModel.procToJava(procedure).toString());
  }
}