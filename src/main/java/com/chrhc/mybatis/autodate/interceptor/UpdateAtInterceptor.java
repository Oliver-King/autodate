package com.chrhc.mybatis.autodate.interceptor;

import com.chrhc.mybatis.autodate.util.PluginUtil;
import com.chrhc.mybatis.autodate.util.TypeUtil;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.JdbcParameter;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.ItemsList;
import net.sf.jsqlparser.expression.operators.relational.MultiExpressionList;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.binding.MapperMethod;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.defaults.DefaultSqlSession;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author cgj
 * @date 2016-06-23
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class UpdateAtInterceptor implements Interceptor {

    private static final Log logger = LogFactory.getLog(UpdateAtInterceptor.class);
    private static final String M = "prepare";
    private static final String SP = ".";
    private static final String FRCH = "__frch";

    private Properties props;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        if (null == props || props.isEmpty() || props.size() != 1) {
            // 只处理 update_at 字段
            return invocation.proceed();
        }

        String interceptMethod = invocation.getMethod().getName();
        if (!M.equals(interceptMethod)) {
            return invocation.proceed();
        }

        StatementHandler handler = (StatementHandler) PluginUtil.processTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(handler);
        MappedStatement ms = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        SqlCommandType sqlCmdType = ms.getSqlCommandType();
        if (sqlCmdType != SqlCommandType.UPDATE && sqlCmdType != SqlCommandType.INSERT) {
            return invocation.proceed();
        }

        StatementType statementType = ms.getStatementType();
        if (statementType != StatementType.PREPARED) {
            return invocation.proceed();
        }

        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();

        //获取原始sql
        String originalSql = boundSql.getSql();
        logger.debug("==> originalSql: " + originalSql);

        try {
            //修改后的sql
            String newSql;

            Date date = new Date();
            if (sqlCmdType == SqlCommandType.UPDATE) {
                newSql = build4Update(sqlCmdType, parameterMappings, parameterObject, originalSql, date);
            } else {
                newSql = build4Insert(sqlCmdType, parameterMappings, parameterObject, originalSql, date);
            }

            //修改原始sql
            if (null != newSql && newSql.length() > 0) {
                logger.debug("==> newSql: " + newSql);
                metaObject.setValue("delegate.boundSql.sql", newSql);
            }

        } catch (Exception e) {
            logger.error("更新时间拦截异常：", e);
        }

        return invocation.proceed();
    }

    /**
     * update 操作
     *
     * @param sqlCmdType        sqlCmdType
     * @param parameterMappings parameterMappings
     * @param parameterObject   parameterObject
     * @param originalSql       originalSql
     * @param date              date
     * @return sql
     * @throws JSQLParserException e
     */
    private String build4Update(SqlCommandType sqlCmdType, List<ParameterMapping> parameterMappings, Object parameterObject, String originalSql, Date date) throws JSQLParserException {
        StringBuilder sb = new StringBuilder();
        for (Object k : props.keySet()) {
            String filedName = (String) k;
            String columnName = props.getProperty(filedName);

            String[] sqlList = originalSql.split(";");
            int length = sqlList.length;
            for (int i = 0; i < length; i++) {
                if (i > 0 && sb.length() > 0) {
                    sb.append(";");
                }

                String sql = sqlList[i];
                Update stmt = (Update) CCJSqlParserUtil.parse(sql);
                List<Column> columns = getColumns(stmt, sqlCmdType);

                if (contains(columns, columnName)) {
                    //包含update_at 字段时，则修改参数值
                    modifyParam4Update(stmt, parameterObject, parameterMappings, length, i, columnName, filedName, date);
                    // sb.append(sql); 暂时不用
                } else {
                    //不包含update_at 字段时，则修改SQL
                    sql = addFiled4Update(stmt, columnName, date);
                    sb.append(sql);

                }
            }
        }
        return sb.toString();
    }

    /**
     * insert 操作
     *
     * @param sqlCmdType        sqlCmdType
     * @param parameterMappings parameterMappings
     * @param parameterObject   parameterObject
     * @param originalSql       originalSql
     * @param date              date
     * @return sql
     * @throws JSQLParserException e
     */
    private String build4Insert(SqlCommandType sqlCmdType, List<ParameterMapping> parameterMappings, Object parameterObject, String originalSql, Date date) throws JSQLParserException {
        StringBuilder sb = new StringBuilder();
        for (Object k : props.keySet()) {
            String filedName = (String) k;
            String columnName = props.getProperty(filedName);

            String[] sqlList = originalSql.split(";");
            int length = sqlList.length;
            for (int i = 0; i < length; i++) {
                if (i > 0 && sb.length() > 0) {
                    sb.append(";");
                }

                String sql = sqlList[i];
                Insert stmt = (Insert) CCJSqlParserUtil.parse(sql);
                List<Column> columns = getColumns(stmt, sqlCmdType);

                if (contains(columns, columnName)) {
                    //包含update_at 字段时，则修改参数值
                    modifyParsm4Insert(stmt, parameterObject, parameterMappings, length, i, columnName, filedName, date);
                    // sb.append(sql); 暂时不用
                } else {
                    //不包含update_at 字段时，则修改SQL
                    sql = addFiled4Insert(stmt, columnName, date);
                    sb.append(sql);

                }
            }
        }
        return sb.toString();
    }

    /**
     * getColumns
     *
     * @param stmt       stmt
     * @param sqlCmdType type
     * @return c
     */
    private List<Column> getColumns(Statement stmt, SqlCommandType sqlCmdType) {
        List<Column> columns;
        if (sqlCmdType == SqlCommandType.UPDATE) {
            Update update = (Update) stmt;
            columns = update.getColumns();
        } else {
            Insert insert = (Insert) stmt;
            columns = insert.getColumns();
        }

        return columns;
    }

    /**
     * update 修改
     *
     * @param date 参数
     * @param stmt stmt
     * @return s
     */
    private String addFiled4Update(Statement stmt, String columnName, Date date) {
        Update update = (Update) stmt;
        Expression expression = new StringValue(getSdf().format(date));
        List<Column> columns = update.getColumns();
        Column column = new Column();
        column.setColumnName(columnName);
        columns.add(column);
        List<Expression> expressions = update.getExpressions();
        expressions.add(expression);
        return stmt.toString();
    }

    /**
     * insert 修改
     *
     * @param columnName 参数
     * @param date       参数
     * @param stmt       stmt
     * @return s
     */
    private String addFiled4Insert(Statement stmt, String columnName, Date date) {

        Insert insert = (Insert) stmt;
        List<Column> columns = insert.getColumns();
        Column column = new Column();
        column.setColumnName(columnName);
        columns.add(column);

        Expression expression = new StringValue(getSdf().format(date));
        ItemsList itemList = insert.getItemsList();
        if (itemList instanceof ExpressionList) {
            //单个,也有可能是批量接口实际只传递了1个
            ExpressionList expressionList = (ExpressionList) itemList;
            List<Expression> expressions = expressionList.getExpressions();
            expressions.add(expression);
        } else if (itemList instanceof MultiExpressionList) {
            //批量
            MultiExpressionList multiExpressionList = (MultiExpressionList) itemList;
            List<ExpressionList> expressionLists = multiExpressionList.getExprList();
            expressionLists.forEach(expressionList -> {
                List<Expression> expressions = expressionList.getExpressions();
                expressions.add(expression);
            });

        } else {
            //insert select
            columns.remove(columns.size() - 1);
        }

        return stmt.toString();

    }

    /**
     * update 修改字段参数值
     *
     * @param stmt              stmt
     * @param parameterObject   parameterObject
     * @param parameterMappings parameterMappings
     * @param frchIdx           frchIdx
     * @param columnName        columnName
     * @param filedName         filedName
     * @param o                 o
     */
    private void modifyParam4Update(Update stmt, Object parameterObject, List<ParameterMapping> parameterMappings, int sqlCn, int frchIdx, String columnName, String filedName, Object o) {
        List<Column> columns = stmt.getColumns();
        int clmIdx = getColumnIndex(columns, columnName);
        int pIdx = getParamAt(stmt, clmIdx);
        setFieldValue(parameterObject, parameterMappings, sqlCn, frchIdx, columns.size(), clmIdx, pIdx, filedName, o);
    }

    /**
     * insert 修改
     *
     * @param stmt            columns
     * @param parameterObject p
     * @param columnName      c
     * @param filedName       f
     * @param o               o
     */
    private void modifyParsm4Insert(Insert stmt, Object parameterObject, List<ParameterMapping> parameterMappings, int sqlCn, int frchIdx, String columnName, String filedName, Object o) {
        List<Column> columns = stmt.getColumns();
        int clmIdx = getColumnIndex(columns, columnName);
        int pIdx = getParamAt(stmt, clmIdx);
        setFieldValue(parameterObject, parameterMappings, sqlCn, frchIdx, columns.size(), clmIdx, pIdx, filedName, o);
    }

    /**
     * 获取列脚标
     *
     * @param columns    c
     * @param columnName cn
     * @return i
     */
    private int getColumnIndex(List<Column> columns, String columnName) {
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            if (column.getColumnName().equalsIgnoreCase(columnName)) {
                return i;
            }
        }
        throw new IllegalArgumentException("列【" + columnName + "】不存在！");
    }

    /**
     * getParamAt
     *
     * @param stmt stmt
     * @return i
     */
    private int getParamAt(Statement stmt, int idx) {
        int pAt = -1;
        if (stmt instanceof Update) {
            Update update = (Update) stmt;
            List<Expression> expressions = update.getExpressions();
            Expression expression = expressions.get(idx);
            JdbcParameter jdbcParameter = (JdbcParameter) expression;
            pAt = jdbcParameter.getIndex();
        } else if (stmt instanceof Insert) {
            Insert insert = (Insert) stmt;
            ExpressionList expressionList = (ExpressionList) insert.getItemsList();
            List<Expression> expressions = expressionList.getExpressions();
            Expression expression = expressions.get(idx);
            JdbcParameter jdbcParameter = (JdbcParameter) expression;
            pAt = jdbcParameter.getIndex();
        }
        return pAt;
    }

    /**
     * setFieldValue
     *
     * @param parameterObject 参数
     * @param clmIdx          clmIdx
     * @param fieldName       f
     */
    private void setFieldValue(Object parameterObject, List<ParameterMapping> parameterMappings, int sqlCn, int frchIdx, int clmCn, int clmIdx, int pIdx, String fieldName, Object v) {
        int pmIdx = (frchIdx * (parameterMappings.size()/sqlCn)) + (pIdx - 1);
        ParameterMapping parameterMapping = parameterMappings.get(pmIdx);
        String property = parameterMapping.getProperty();
        logger.debug(pIdx + ":" + property);
        String pKey = property;

        if (pKey.startsWith(FRCH)) {
            logger.warn("暂不支持 【" + pKey + "】" + frchIdx + " ==> " + clmIdx);
            return;
        }

        if (pKey.contains(SP)) {
            String[] split = pKey.split("\\.");
            pKey = split[0];
        }

        if (parameterObject instanceof DefaultSqlSession.StrictMap) {
            logger.warn("暂不支持 DefaultSqlSession.StrictMap");
        } else if (parameterObject instanceof MapperMethod.ParamMap<?>) {
            MapperMethod.ParamMap map = (MapperMethod.ParamMap) parameterObject;

            Integer pAt = null;
            if (isPKeyArray(pKey)) {
                String[] split = pKey.split("\\[");
                pKey = split[0];
                split = split[1].split("]");
                String s = split[0];
                pAt = Integer.parseInt(s);
            }

            Object param1 = map.get(pKey);
            if (param1.getClass().isArray() || List.class.isAssignableFrom(param1.getClass())) {
                // logger.warn("暂不支持 Array"); 尝试支持
                setValueFromListOrArray(param1, frchIdx, clmIdx, pIdx, pAt, fieldName, v);
            } else {
                setVal(fieldName, v, pKey, map, param1);
            }
        } else if (parameterObject instanceof Map) {
            Map map = (Map) parameterObject;
            map.put(pKey, v);
        } else {
            MetaObject metaObject = SystemMetaObject.forObject(parameterObject);
            metaObject.setValue(fieldName, v);
        }
    }

    /**
     * 设置值
     *
     * @param fieldName fieldName
     * @param v         v
     * @param pKey      pk
     * @param map       m
     * @param param1    p
     */
    private void setVal(String fieldName, Object v, String pKey, MapperMethod.ParamMap map, Object param1) {
        if (TypeUtil.isSimpleType(param1.getClass())) {
            map.put(pKey, v);
        } else {
            MetaObject metaObject = SystemMetaObject.forObject(param1);
            metaObject.setValue(fieldName, v);
        }
    }

    /**
     * getValueFromListOrArray
     *
     * @param param     o
     * @param index     i
     * @param fieldName f
     */
    private void setValueFromListOrArray(Object param, int frchIdx, int index, int pIdx, Integer pAt, String fieldName, Object v) {
        Object entity;
        if (param instanceof List) {
            List list = (List) param;
            entity = list.get(pAt);
            if (entity != null) {
                if (TypeUtil.isSimpleType(entity.getClass())) {
                    list.set(pAt, v);
                } else {
                    MetaObject metaObject = SystemMetaObject.forObject(entity);
                    metaObject.setValue(fieldName, v);
                }
            }
        } else if (param != null && param.getClass().isArray()) {
            Object[] objects = (Object[]) param;
            entity = objects[pAt];
            if (entity != null) {
                if (TypeUtil.isSimpleType(entity.getClass())) {
                    objects[pAt] = v;
                } else {
                    MetaObject metaObject = SystemMetaObject.forObject(entity);
                    metaObject.setValue(fieldName, v);
                }
            }
        }
    }

    /**
     * 判断参数是否为数组型参数
     *
     * @param pKey pk
     * @return b
     */
    private boolean isPKeyArray(String pKey) {
        String pattern = "^\\w+\\[\\w+]$";
        return Pattern.matches(pattern, pKey);
    }

    /**
     * 判断是否存在
     *
     * @param columns    c
     * @param columnName cn
     * @return b
     */
    private boolean contains(List<Column> columns, String columnName) {
        if (columns == null || columns.isEmpty()) {
            return false;
        }
        if (columnName == null || columnName.length() <= 0) {
            return false;
        }
        for (Column column : columns) {
            if (column.getColumnName().equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        } else {
            return target;
        }
    }

    @Override
    public void setProperties(Properties properties) {
        if (null != properties && !properties.isEmpty()) {
            props = properties;
        }
    }

    private SimpleDateFormat getSdf() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    }

}
