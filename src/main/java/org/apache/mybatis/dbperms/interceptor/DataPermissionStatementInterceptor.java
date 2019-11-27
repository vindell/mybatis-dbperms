/**
 * Copyright (c) 2018, vindell (https://github.com/vindell).
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.mybatis.dbperms.interceptor;

import java.lang.reflect.Method;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.meta.MetaStatementHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.utils.MetaObjectUtils;
import org.apache.mybatis.dbperms.annotation.RequiresPermission;
import org.apache.mybatis.dbperms.annotation.RequiresPermissions;
import org.apache.mybatis.dbperms.parser.TablePermissionAnnotationParser;
import org.apache.mybatis.dbperms.parser.TablePermissionAutowireParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;

public class DataPermissionStatementInterceptor extends AbstractDataPermissionInterceptor {

	protected static Logger LOG = LoggerFactory.getLogger(DataPermissionStatementInterceptor.class);
	protected TablePermissionAutowireParser autowirePermissionParser;
	protected TablePermissionAnnotationParser annotationPermissionParser;
	
	public DataPermissionStatementInterceptor(TablePermissionAutowireParser autowirePermissionParser) {
		this.autowirePermissionParser = autowirePermissionParser;
	}
	
	@Override
	public Object doStatementIntercept(Invocation invocation, StatementHandler statementHandler,MetaStatementHandler metaStatementHandler) throws Throwable {
		
		//检查是否需要进行拦截处理
		if (isRequireIntercept(invocation, statementHandler, metaStatementHandler)) {
			// 利用反射获取到FastResultSetHandler的mappedStatement属性，从而获取到MappedStatement；
			//MappedStatement mappedStatement = metaStatementHandler.getMappedStatement();
						
			// 获取对应的BoundSql，这个BoundSql其实跟我们利用StatementHandler获取到的BoundSql是同一个对象。
			BoundSql boundSql = metaStatementHandler.getBoundSql();
			MetaObject metaBoundSql = MetaObjectUtils.forObject(boundSql);
			// 原始SQL
			String originalSQL = (String) metaBoundSql.getValue("sql");
			//提取被国际化注解标记的方法
			Method method = metaStatementHandler.getMethod(); 
			// Method method = BeanMethodDefinitionFactory.getMethodDefinition(mappedStatement.getId());
			// 获取 @RequiresPermissions 注解标记
			RequiresPermissions permissions = AnnotationUtils.findAnnotation(method, RequiresPermissions.class);
			// 需要权限控制
			if(permissions != null) {
				// 框架自动进行数据权限注入
				if(permissions.autowire()) {
					originalSQL = autowirePermissionParser.parser(metaStatementHandler, originalSQL);
				}
				else if(ArrayUtils.isNotEmpty(permissions.value())){
					originalSQL = annotationPermissionParser.parser(metaStatementHandler, originalSQL, permissions);
				}
				// 将处理后的物理分页sql重新写入作为执行SQL
				metaBoundSql.setValue("sql", originalSQL);
				if (LOG.isDebugEnabled()) {
					LOG.debug(" Permissioned SQL : "+ statementHandler.getBoundSql().getSql());
				}
			} else {
				// 获取 @RequiresPermission 注解标记
				RequiresPermission permission = AnnotationUtils.findAnnotation(method, RequiresPermission.class);
				if (permission != null) {
					originalSQL = annotationPermissionParser.parser(metaStatementHandler, originalSQL, permission);
					// 将处理后的物理分页sql重新写入作为执行SQL
					metaBoundSql.setValue("sql", originalSQL);
					if (LOG.isDebugEnabled()) {
						LOG.debug(" Permissioned SQL : "+ statementHandler.getBoundSql().getSql());
					}
				}
			}
		}
		// 将执行权交给下一个拦截器  
		return invocation.proceed();
	}
	
	@Override
	public Object plugin(Object target) {
		if (target instanceof StatementHandler) {  
            return Plugin.wrap(target, this);  
        } else {  
            return target;  
        }
	}
	
}