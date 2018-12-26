package com.chrhc.mybatis.autodate.interceptor;

import com.chrhc.mybatis.autodate.util.PluginUtil;
import com.chrhc.mybatis.autodate.util.TypeUtil;
import net.sf.jsqlparser.expression.Expression;
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
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
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

/**
 * @author cgj
 * @date 2016-06-23
 */
@Intercepts({@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})})
public class UpdateAtInterceptor implements Interceptor {

    private static final Log logger = LogFactory.getLog(UpdateAtInterceptor.class);
    private static final String M = "prepare";
    private static final String PARAM = "param";
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

        BoundSql boundSql = (BoundSql) metaObject.getValue("delegate.boundSql");
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Object parameterObject = boundSql.getParameterObject();

        //获取原始sql
        String originalSql = (String) metaObject.getValue("delegate.boundSql.sql");
        logger.debug("==> originalSql: " + originalSql);

        //修改后的sql
        String newSql = "";

        Date date = new Date();
        if (sqlCmdType == SqlCommandType.UPDATE) {

            for (Object k : props.keySet()) {
                String filedName = (String) k;
                String columnName = props.getProperty(filedName);

                StringBuilder sb = new StringBuilder();
                String[] sqlList = originalSql.split(";");
                for (int i = 0; i < sqlList.length; i++) {
                    if (i > 0) {
                        sb.append(";");
                    }

                    String sql = sqlList[i];
                    Statement stmt = CCJSqlParserUtil.parse(sql);
                    List<Column> columns = getColumns(stmt, sqlCmdType);

                    if (!contains(columns, columnName)) {
                        //不包含update_at 字段时，则修改SQL
                        sql = addFiled4Update(stmt, columnName, date);
                        sb.append(sql);
                    } else {
                        //包含update_at 字段时，则修改参数值
                        modifyParam4Update(columns, parameterObject, parameterMappings, columnName, filedName, date);
                    }
                }
                newSql = sb.toString();
            }
        } else {
            Statement stmt = CCJSqlParserUtil.parse(originalSql);
            List<Column> columns = getColumns(stmt, sqlCmdType);

            for (Object k : props.keySet()) {
                String filedName = (String) k;
                String columnName = props.getProperty(filedName);

                if (!contains(columns, columnName)) {
                    //不包含update_at 字段时，则修改SQL
                    newSql = addFiled4Insert(stmt, columnName, date);
                } else {
                    //包含update_at 字段时，则修改参数值
                    modifyparsm4Insert(columns, parameterObject, parameterMappings, columnName, filedName, date);
                }
            }
        }

        //修改原始sql
        if (null != newSql && newSql.length() > 0) {
            logger.debug("==> newSql: " + newSql);
            metaObject.setValue("delegate.boundSql.sql", newSql);
        }

        return invocation.proceed();
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
     * @param columns    update
     * @param columnName c
     * @param o          o
     * @return s
     */
    private void modifyParam4Update(List<Column> columns, Object parameterObject, List<ParameterMapping> parameterMappings, String columnName, String filedName, Object o) {
        int idx = getColumnIndex(columns, columnName);
        setFieldValue(parameterObject, idx, filedName, o);

    }

    /**
     * insert 修改
     *
     * @param columns         columns
     * @param parameterObject p
     * @param columnName      c
     * @param filedName       f
     * @param o               o
     */
    private void modifyparsm4Insert(List<Column> columns, Object parameterObject, List<ParameterMapping> parameterMappings, String columnName, String filedName, Object o) {
        int idx = getColumnIndex(columns, columnName);
        setFieldValue(parameterObject, idx, filedName, o);
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
     * setFieldValue
     *
     * @param parameterObject 参数
     * @param index           index
     * @param fieldName       f
     */
    private void setFieldValue(Object parameterObject, int index, String fieldName, Object v) {
        String paramIdx = PARAM + index;
        if (parameterObject instanceof DefaultSqlSession.StrictMap) {
            DefaultSqlSession.StrictMap map = (DefaultSqlSession.StrictMap) parameterObject;
            Object object = map.get("list");
            if (object == null) {
                object = map.get("array");
            }
            if (object != null) {
                setValueFromListOrArray(object, index, fieldName, v);
            }
        } else if (parameterObject instanceof MapperMethod.ParamMap<?>) {
            MapperMethod.ParamMap map = (MapperMethod.ParamMap) parameterObject;
            Object param1 = map.get(paramIdx);
            if (param1.getClass().isArray() || List.class.isAssignableFrom(param1.getClass())) {
                setValueFromListOrArray(param1, index, fieldName, v);
            } else {
                if (TypeUtil.isSimpleType(param1.getClass())) {
                    map.put(paramIdx, v);
                } else {
                    MetaObject metaObject = SystemMetaObject.forObject(param1);
                    metaObject.setValue(fieldName, v);
                }
            }
        } else if (parameterObject instanceof Map) {
            Map map = (Map) parameterObject;
            map.put(paramIdx, v);
        } else {
            MetaObject metaObject = SystemMetaObject.forObject(parameterObject);
            metaObject.setValue(fieldName, v);
        }

    }

    /**
     * getValueFromListOrArray
     *
     * @param parameterObject o
     * @param index           i
     * @param fieldName       f
     */
    private void setValueFromListOrArray(Object parameterObject, int index, String fieldName, Object v) {
        Object entity = null;
        if (parameterObject instanceof List) {
            entity = ((List) parameterObject).get(index);
        } else if (parameterObject != null && parameterObject.getClass().isArray()) {
            entity = ((Object[]) parameterObject)[index];
        }
        if (entity != null) {
            MetaObject metaObject = SystemMetaObject.forObject(entity);
            metaObject.setValue(fieldName, v);
        }
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
