/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */

package org.elasticsearch.xpack.enrich.action;

import org.elasticsearch.ResourceNotFoundException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.xpack.core.enrich.EnrichPolicy;
import org.elasticsearch.xpack.core.enrich.action.DeleteEnrichPolicyAction;
import org.elasticsearch.xpack.core.enrich.action.GetEnrichPolicyAction;
import org.elasticsearch.xpack.enrich.AbstractEnrichTestCase;
import org.junit.After;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.elasticsearch.xpack.enrich.EnrichPolicyTests.assertEqualPolicies;
import static org.elasticsearch.xpack.enrich.EnrichPolicyTests.randomEnrichPolicy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.IsInstanceOf.instanceOf;

public class TransportGetEnrichPolicyActionTests extends AbstractEnrichTestCase {

    @After
    private void cleanupPolicies() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<GetEnrichPolicyAction.Response> reference = new AtomicReference<>();
        final TransportGetEnrichPolicyAction transportAction = node().injector().getInstance(TransportGetEnrichPolicyAction.class);
        transportAction.execute(null,
            new GetEnrichPolicyAction.Request(),
            new ActionListener<GetEnrichPolicyAction.Response>() {
                @Override
                public void onResponse(GetEnrichPolicyAction.Response response) {
                    reference.set(response);
                    latch.countDown();

                }

                public void onFailure(final Exception e) {
                    fail();
                }
            });
        latch.await();
        assertNotNull(reference.get());
        GetEnrichPolicyAction.Response response = reference.get();

        for (EnrichPolicy.NamedPolicy policy: response.getPolicies()) {
            final CountDownLatch loopLatch = new CountDownLatch(1);
            final AtomicReference<AcknowledgedResponse> loopReference = new AtomicReference<>();
            final TransportDeleteEnrichPolicyAction deleteAction = node().injector().getInstance(TransportDeleteEnrichPolicyAction.class);
            deleteAction.execute(null,
                new DeleteEnrichPolicyAction.Request(policy.getName()),
                new ActionListener<AcknowledgedResponse>() {
                    @Override
                    public void onResponse(AcknowledgedResponse acknowledgedResponse) {
                        loopReference.set(acknowledgedResponse);
                        loopLatch.countDown();
                    }

                    public void onFailure(final Exception e) {
                        fail();
                    }
                });
            loopLatch.await();
            assertNotNull(loopReference.get());
            assertTrue(loopReference.get().isAcknowledged());
        }
    }

    public void testListPolicies() throws InterruptedException {
        EnrichPolicy policy = randomEnrichPolicy(XContentType.JSON);
        ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        String name = "my-policy";

        AtomicReference<Exception> error = saveEnrichPolicy(name, policy, clusterService);
        assertThat(error.get(), nullValue());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<GetEnrichPolicyAction.Response> reference = new AtomicReference<>();
        final TransportGetEnrichPolicyAction transportAction = node().injector().getInstance(TransportGetEnrichPolicyAction.class);
        transportAction.execute(null,
            // empty or null should return the same
            randomBoolean() ? new GetEnrichPolicyAction.Request() : new GetEnrichPolicyAction.Request(""),
            new ActionListener<GetEnrichPolicyAction.Response>() {
                @Override
                public void onResponse(GetEnrichPolicyAction.Response response) {
                    reference.set(response);
                    latch.countDown();

                }

                public void onFailure(final Exception e) {
                    fail();
                }
            });
        latch.await();
        assertNotNull(reference.get());
        GetEnrichPolicyAction.Response response = reference.get();

        assertThat(response.getPolicies().size(), equalTo(1));

        EnrichPolicy.NamedPolicy actualPolicy = response.getPolicies().get(0);
        assertThat(name, equalTo(actualPolicy.getName()));
        assertEqualPolicies(policy, actualPolicy.getPolicy());
    }

    public void testListEmptyPolicies() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<GetEnrichPolicyAction.Response> reference = new AtomicReference<>();
        final TransportGetEnrichPolicyAction transportAction = node().injector().getInstance(TransportGetEnrichPolicyAction.class);
        transportAction.execute(null,
            new GetEnrichPolicyAction.Request(),
            new ActionListener<GetEnrichPolicyAction.Response>() {
                @Override
                public void onResponse(GetEnrichPolicyAction.Response response) {
                    reference.set(response);
                    latch.countDown();

                }

                public void onFailure(final Exception e) {
                    fail();
                }
            });
        latch.await();
        assertNotNull(reference.get());
        GetEnrichPolicyAction.Response response = reference.get();

        assertThat(response.getPolicies().size(), equalTo(0));
    }

    public void testGetPolicy() throws InterruptedException {
        EnrichPolicy policy = randomEnrichPolicy(XContentType.JSON);
        ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        String name = "my-policy";

        AtomicReference<Exception> error = saveEnrichPolicy(name, policy, clusterService);
        assertThat(error.get(), nullValue());

        // save a second one to verify the count below on GET
        error = saveEnrichPolicy("something-else", randomEnrichPolicy(XContentType.JSON), clusterService);
        assertThat(error.get(), nullValue());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<GetEnrichPolicyAction.Response> reference = new AtomicReference<>();
        final TransportGetEnrichPolicyAction transportAction = node().injector().getInstance(TransportGetEnrichPolicyAction.class);
        transportAction.execute(null,
            new GetEnrichPolicyAction.Request(name),
            new ActionListener<GetEnrichPolicyAction.Response>() {
                @Override
                public void onResponse(GetEnrichPolicyAction.Response response) {
                    reference.set(response);
                    latch.countDown();

                }

                public void onFailure(final Exception e) {
                    fail();
                }
            });
        latch.await();
        assertNotNull(reference.get());
        GetEnrichPolicyAction.Response response = reference.get();

        assertThat(response.getPolicies().size(), equalTo(1));

        EnrichPolicy.NamedPolicy actualPolicy = response.getPolicies().get(0);
        assertThat(name, equalTo(actualPolicy.getName()));
        assertEqualPolicies(policy, actualPolicy.getPolicy());
    }

    public void testGetPolicyThrowsError() throws InterruptedException {
        EnrichPolicy policy = randomEnrichPolicy(XContentType.JSON);
        ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        String name = "my-policy";

        AtomicReference<Exception> error = saveEnrichPolicy(name, policy, clusterService);
        assertThat(error.get(), nullValue());

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Exception> reference = new AtomicReference<>();
        final TransportGetEnrichPolicyAction transportAction = node().injector().getInstance(TransportGetEnrichPolicyAction.class);
        transportAction.execute(null,
            new GetEnrichPolicyAction.Request("non-exists"),
            new ActionListener<GetEnrichPolicyAction.Response>() {
                @Override
                public void onResponse(GetEnrichPolicyAction.Response response) {
                    fail();
                }

                public void onFailure(final Exception e) {
                    reference.set(e);
                    latch.countDown();
                }
            });
        latch.await();
        assertNotNull(reference.get());
        assertThat(reference.get(), instanceOf(ResourceNotFoundException.class));
        assertThat(reference.get().getMessage(),
            equalTo("Policy [non-exists] was not found"));
    }
}
