package cz.lbenda.spmodel;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.lbenda.spmodel.annotation.StoredProcedure;
import cz.lbenda.spmodel.annotation.StoredProcedureParam;

/**
 * Factory which crate procedures and execute it for sybase
 * @author lbenda on 9/10/17.
 */
public class SPFactory {

  private static final Logger LOG = LoggerFactory.getLogger(SPFactory.class);

  interface SPHolderInterface {
    Map<String, Object> __parameterValues();
  }

  @SuppressWarnings("unchecked")
  public <T> T procedureInstance(Class<T> type) {
    Class proxyClass = Proxy.getProxyClass(type.getClassLoader(), type);
    try {
      return (T) proxyClass.getConstructor(InvocationHandler.class, SPHolderInterface.class).newInstance(new SPHandler());
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
      LOG.error("Problem with create procedure instance", e);
      throw new RuntimeException(e);
    }
  }

  protected String createQuery(ProcedureDescription pd) {
    return null;
  }

  public Stream<Method> annMethod(Class clazz) {
    List<Method> methods = new ArrayList<>();
    Stream met = Arrays.stream(clazz.getDeclaredMethods()).filter(method -> method.getAnnotation(StoredProcedureParam.class) != null);
    if (!clazz.isInterface()) {
      met = Stream.concat(met, Arrays.stream(clazz.getInterfaces()).flatMap(cl -> annMethod(cl)));
    }
    if (!Object.class.equals(clazz.getSuperclass()) || clazz.getSuperclass() != null) {
      return Stream.concat(met, annMethod(clazz.getSuperclass()));
    }
    return met;
  }

  public <T> void executeProcedure(Connection conn, T procedure) throws SQLException {
    Class clazz = procedure.getClass();

    StoredProcedure sp = (StoredProcedure) clazz.getAnnotation(StoredProcedure.class);
    ProcedureDescription pd = new ProcedureDescription();
    pd.setName(sp.name());
    pd.setCatalag(sp.catalog());
    pd.setSchema(sp.schema());

    annMethod(clazz).forEach(method -> {
      StoredProcedureParam spp = method.getAnnotation(StoredProcedureParam.class);
      ParamDescription pdd = pd.getParams().stream().filter(param -> param.getName().equals(spp.name())).findFirst().orElseGet(() -> {
        ParamDescription param = new ParamDescription();
        pd.getParams().add(param);
        param.setName(spp.name());
        param.setOrder(spp.order());
        param.setSqlType(spp.sqlType());
        return param;
      });
      if (method.getName().startsWith("get")) {
        pdd.setOut(true);
        pdd.setJavaClass(method.getReturnType());
        pdd.setNullable(!pdd.getJavaClass().isPrimitive() && null == method.getAnnotation(NotNull.class));
      } else {
        pdd.setIn(true);
        pdd.setJavaClass(method.getParameterTypes()[0]);
        pdd.setNullable(pdd.getJavaClass().isPrimitive());
        pdd.setNullable(!pdd.getJavaClass().isPrimitive() && Arrays.stream(method.getParameterAnnotations()[0]).noneMatch(a -> a.equals(NotNull.class)));
      }
    });

    SPHolderInterface holder = (SPHolderInterface) procedure;
    try (CallableStatement ps = conn.prepareCall(createQuery(pd))) {
      int i = 1;
      for (ParamDescription param : pd.getParams()) {
        Object value = holder.__parameterValues().get(param.getName());
        if (value == null) {
          ps.setNull(i, param.getSqlType());
        } else {
          ps.setObject(i, value);
        }
        i++;
      }
      ps.executeQuery();
    }
  }

  private static class SPHandler implements InvocationHandler {

    private final Map<String, Object> values = new HashMap<>();
    private final Method parameters;

    SPHandler() {
      try {
        parameters = SPHolderInterface.class.getMethod("__parameterValues");
      } catch (NoSuchMethodException e) {
        LOG.error(e.toString(), e);
        throw new RuntimeException(e);
      }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (method.equals(parameters)) {
        return values;
      }
      StoredProcedureParam spp = method.getAnnotation(StoredProcedureParam.class);
      if (spp == null) { return null; }

      if (method.getReturnType() != null && !Void.class.equals(method.getReturnType()) && !Void.TYPE.equals(method.getReturnType())) {
        return values.get(spp.name());
      }
      values.put(spp.name(), args[0]);
      return null;
    }
  }
}
