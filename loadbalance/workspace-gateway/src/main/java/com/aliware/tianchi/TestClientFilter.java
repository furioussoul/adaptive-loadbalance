package com.aliware.tianchi;

import org.apache.dubbo.common.Constants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Filter;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author daofeng.xjf
 * <p>
 * 客户端过滤器
 * 可选接口
 * 用户可以在客户端拦截请求和响应,捕获 rpc 调用时产生、服务端返回的已知异常。
 */
@Activate(group = Constants.CONSUMER)
public class TestClientFilter implements Filter {



    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        try {
            Result result = invoker.invoke(invocation);
            int port = invoker.getUrl().getPort();
            AtomicInteger atomicInteger = UserLoadBalance.errorMap.get(port);
            int count = atomicInteger.decrementAndGet();

            String quotaName = UserLoadBalance.portToQuotaName.get(port);
            if (count < 3 && UserLoadBalance.exclude.contains(quotaName) && CallbackListenerImpl.map.size() == 3) {
                synchronized (CallbackListenerImpl.class) {
                    System.out.println("error < 3");
                    CallbackListenerImpl.createQueue();
                    UserLoadBalance.exclude.remove(quotaName);
                }
            }

            return result;
        } catch (Exception e) {
            int port = invoker.getUrl().getPort();
            AtomicInteger atomicInteger = UserLoadBalance.errorMap.get(port);
            int count = atomicInteger.incrementAndGet();
            if (count >= 10) {
                System.out.println("error >= 10");
                String quotaName = UserLoadBalance.portToQuotaName.get(port);
                UserLoadBalance.exclude.add(quotaName);
            }
            throw e;
        }

    }

    @Override
    public Result onResponse(Result result, Invoker<?> invoker, Invocation invocation) {
        if (CallbackListenerImpl.queue != null) {
            CallbackListenerImpl.queue.offer(UserLoadBalance.portToQuotaName.get(invoker.getUrl().getPort()));
        }
        return result;
    }
}
