package cz.lbenda.spmodel;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * Description of procedure param
 * @author lbenda on 9/9/17.
 */
public class ParamDescription {

    private ProcedureDescription procedure; public ProcedureDescription getProcedure() { return procedure; } public void setProcedure(ProcedureDescription procedure) { this.procedure = procedure; }
    private String name; public String getName() { return name; } public void setName(String name) { this.name = name; }
    private Class javaClass; public Class getJavaClass() { return javaClass; } public void setJavaClass(Class javaClass) { this.javaClass = javaClass; }
    private int sqlType; public int getSqlType() { return sqlType; } public void setSqlType(int sqlType) { this.sqlType = sqlType; }
    private boolean in; public boolean isIn() { return in; } public void setIn(boolean in) { this.in = in; }
    private boolean out; public boolean isOut() { return out; } public void setOut(boolean out) { this.out = out; }
    private int length; public int getLength() { return length; } public void setLength(int length) { this.length = length; }
    private int precision; public int getPrecision() { return precision; } public void setPrecision(int precision) { this.precision = precision; }
    private int scale; public int getScale() { return scale; } public void setScale(int scale) { this.scale = scale; }
    private int radix; public int getRadix() { return radix; } public void setRadix(int radix) { this.radix = radix; }
    private boolean nullable; public boolean isNullable() { return nullable; } public void setNullable(boolean nullable) { this.nullable = nullable; }
    private int order; public int getOrder() { return order; } public void setOrder(int order) { this.order = order; }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
            .append("procedure", procedure)
            .append("name", name)
            .append("javaClass", javaClass)
            .append("sqlType", sqlType)
            .append("in", in)
            .append("out", out)
            .append("length", length)
            .append("precision", precision)
            .append("scale", scale)
            .append("nullable", nullable)
            .append("radix", radix)
            .append("order", order)
            .toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        ParamDescription rhs = (ParamDescription) obj;
        return new EqualsBuilder()
                .append(this.procedure, rhs.procedure)
                .append(this.name, rhs.name)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(procedure)
                .append(name)
                .toHashCode();
    }
}
