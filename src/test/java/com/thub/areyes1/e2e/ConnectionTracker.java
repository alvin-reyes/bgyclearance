/**
 * Class File Name: ConnectionTracker.java
 * Description: Counts JDBC connections the app opens and closes.
 */

package com.thub.areyes1.e2e;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Register this with the Spring context to wrap the app's DataSource, so tests
 * can assert that every connection the app opens is closed again.
 */
public class ConnectionTracker implements BeanPostProcessor {

	private final AtomicInteger open = new AtomicInteger();

	/**
	 * @return connections opened by the app that have not been closed yet
	 */
	public int openConnections() {
		return open.get();
	}

	public Object postProcessBeforeInitialization(Object bean, String beanName) {
		return bean;
	}

	public Object postProcessAfterInitialization(final Object bean, String beanName) {
		if (!(bean instanceof DataSource)) {
			return bean;
		}
		return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
				new InvocationHandler() {
					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						Object result = call(bean, method, args);
						if ("getConnection".equals(method.getName())) {
							open.incrementAndGet();
							return track((Connection) result);
						}
						return result;
					}
				});
	}

	private Connection track(final Connection conn) {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Connection.class},
				new InvocationHandler() {
					private boolean closed;

					public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
						if ("close".equals(method.getName()) && !closed) {
							closed = true;
							open.decrementAndGet();
						}
						return call(conn, method, args);
					}
				});
	}

	private static Object call(Object target, Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}
}
