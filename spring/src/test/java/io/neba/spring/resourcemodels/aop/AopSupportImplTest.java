package io.neba.spring.resourcemodels.aop;

import org.junit.Test;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.Advised;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author Olaf Otto
 */
public class AopSupportImplTest {
    private static class Model {
        Model() {}
    }

    private Object model;
    private Object modelForInjection;

    private AopSupportImpl testee = new AopSupportImpl();

    @Test
    public void testTargetInstanceIsReturnedForAopAdvisedBeans() throws Exception {
        withAdvisedModel();
        prepareModelForFieldInjection();
        assertModelForInjectionIsTargetOfAdvisedModel();
    }

    @Test
    public void testSameInstanceIsReturnedForNonAopModel() {
        withNonAdvisedModel();
        prepareModelForFieldInjection();
        assertModelForInjectionIsOriginalModel();
    }

    private void assertModelForInjectionIsOriginalModel() {
        assertThat(this.modelForInjection).isSameAs(this.model);
    }

    private void withNonAdvisedModel() {
        this.model = new Object();
    }

    private void assertModelForInjectionIsTargetOfAdvisedModel() {
        assertThat(this.modelForInjection).isExactlyInstanceOf(Model.class);
    }

    private void prepareModelForFieldInjection() {
        this.modelForInjection = this.testee.prepareForFieldInjection(this.model);
    }

    private void withAdvisedModel() throws Exception {
        Advised advised = mock(Advised.class);
        TargetSource targetSource = mock(TargetSource.class);
        Model unwrappedBeanInstance = new Model();
        doReturn(targetSource).when(advised).getTargetSource();
        doReturn(unwrappedBeanInstance).when(targetSource).getTarget();
        this.model = advised;
    }
}