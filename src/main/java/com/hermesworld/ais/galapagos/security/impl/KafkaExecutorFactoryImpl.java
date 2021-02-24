package com.hermesworld.ais.galapagos.security.impl;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.security.concurrent.DelegatingSecurityContextExecutorService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.hermesworld.ais.galapagos.kafka.KafkaExecutorFactory;

/**
 * Implementation of the {@link KafkaExecutorFactory} interface which returns a
 * {@link DelegatingSecurityContextExecutorService} based on a simple single-threaded executor. This allows Galapagos
 * services like <code>TopicService</code> to access the Security Context (via <code>CurrentUserService</code>) and
 * determine the current user even in sub-Threds.
 *
 * @author AlbrechtFlo
 *
 */
@Component
public class KafkaExecutorFactoryImpl implements KafkaExecutorFactory {

    @Override
    public ExecutorService newExecutor() {
        return new DelegatingSecurityContextExecutorService(Executors.newSingleThreadExecutor(),
                SecurityContextHolder.getContext());
    }

}
