package com.hermesworld.ais.galapagos.changes.impl;

import java.beans.PropertyDescriptor;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import com.hermesworld.ais.galapagos.changes.ApplyChangeContext;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionMetadata;
import com.hermesworld.ais.galapagos.subscriptions.SubscriptionState;
import com.hermesworld.ais.galapagos.subscriptions.service.SubscriptionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.beans.BeanUtils;

class ChangeBaseTest {

    /**
     * Tests that each private field in all of the subclasses of ChangeBase have a Getter for all of their private
     * fields, because this getter is highly important for correct serialization of the changes. This test makes heavy
     * use of Reflection!
     */
    @Test
    void testGettersForFields() {
        String packageName = ChangeBase.class.getPackageName();
        ClassLoader cl = ChangeBaseTest.class.getClassLoader();

        // get class candidates
        File f = new File("target/classes");
        f = new File(f, packageName.replace('.', '/'));
        File[] classFiles = f.listFiles((dir, name) -> name.endsWith(".class"));

        assertNotNull(classFiles);
        for (File cf : classFiles) {
            String className = packageName + "." + cf.getName().replace(".class", "");

            Class<?> clazz;
            try {
                clazz = cl.loadClass(className);
            }
            catch (Throwable t) {
                // ignore! May be something else
                continue;
            }
            if (ChangeBase.class.isAssignableFrom(clazz) && !clazz.getName().equals(ChangeBase.class.getName())) {
                assertGettersForFields(clazz);
            }
        }
    }

    private void assertGettersForFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();
        for (Field f : fields) {
            int mods = f.getModifiers();
            if (Modifier.isPrivate(mods) && !Modifier.isStatic(mods)) {
                PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(clazz, f.getName());
                if (pd == null || pd.getReadMethod() == null) {
                    fail("No getter for property " + f.getName() + " in class " + clazz.getName());
                }
            }
        }
    }

    @Test
    void testStageSubscription() throws Exception {
        SubscriptionMetadata sub1 = new SubscriptionMetadata();
        sub1.setId("123");
        sub1.setClientApplicationId("app-1");
        sub1.setTopicName("topic-1");
        sub1.setState(SubscriptionState.PENDING);

        AtomicBoolean createCalled = new AtomicBoolean();

        SubscriptionService subscriptionService = mock(SubscriptionService.class);
        when(subscriptionService.addSubscription(any(), any(), any(), any()))
                .thenThrow(UnsupportedOperationException.class);

        when(subscriptionService.addSubscription(any(), any())).then(inv -> {
            createCalled.set(true);
            SubscriptionMetadata sub = new SubscriptionMetadata();
            sub.setId("999");
            sub.setState(SubscriptionState.APPROVED);
            sub.setTopicName("topic-1");
            sub.setClientApplicationId("app-1");
            return CompletableFuture.completedFuture(sub);
        });

        when(subscriptionService.updateSubscriptionState(any(), any(), any()))
                .thenThrow(UnsupportedOperationException.class);

        ApplyChangeContext context = mock(ApplyChangeContext.class);
        when(context.getTargetEnvironmentId()).thenReturn("target");
        when(context.getSubscriptionService()).thenReturn(subscriptionService);

        ChangeBase.subscribeTopic(sub1).applyTo(context).get();

        assertTrue(createCalled.get());
    }
}
