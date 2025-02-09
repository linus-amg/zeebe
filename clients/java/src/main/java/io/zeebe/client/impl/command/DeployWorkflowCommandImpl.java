/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.client.impl.command;

import static io.zeebe.util.EnsureUtil.ensureNotNull;

import com.google.protobuf.ByteString;
import io.zeebe.client.api.ZeebeFuture;
import io.zeebe.client.api.command.ClientException;
import io.zeebe.client.api.command.DeployWorkflowCommandStep1;
import io.zeebe.client.api.command.DeployWorkflowCommandStep1.DeployWorkflowCommandBuilderStep2;
import io.zeebe.client.api.command.FinalCommandStep;
import io.zeebe.client.api.response.DeploymentEvent;
import io.zeebe.client.impl.ZeebeClientFutureImpl;
import io.zeebe.client.impl.response.DeploymentEventImpl;
import io.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowRequest;
import io.zeebe.gateway.protocol.GatewayOuterClass.DeployWorkflowResponse;
import io.zeebe.gateway.protocol.GatewayOuterClass.WorkflowRequestObject;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.util.StreamUtil;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class DeployWorkflowCommandImpl
    implements DeployWorkflowCommandStep1, DeployWorkflowCommandBuilderStep2 {

  private final DeployWorkflowRequest.Builder request = DeployWorkflowRequest.newBuilder();
  private final GatewayStub asyncStub;
  private Duration requestTimeout;

  public DeployWorkflowCommandImpl(GatewayStub asyncStub, Duration requestTimeout) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addResourceBytes(
      final byte[] resource, final String resourceName) {

    request.addWorkflows(
        WorkflowRequestObject.newBuilder()
            .setType(WorkflowRequestObject.ResourceType.FILE)
            .setName(resourceName)
            .setDefinition(ByteString.copyFrom(resource)));

    return this;
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addResourceString(
      final String resource, final Charset charset, final String resourceName) {
    return addResourceBytes(resource.getBytes(charset), resourceName);
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addResourceStringUtf8(
      final String resourceString, final String resourceName) {
    return addResourceString(resourceString, StandardCharsets.UTF_8, resourceName);
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addResourceStream(
      final InputStream resourceStream, final String resourceName) {
    ensureNotNull("resource stream", resourceStream);

    try {
      final byte[] bytes = StreamUtil.read(resourceStream);

      return addResourceBytes(bytes, resourceName);
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy bpmn resource from stream. %s", e.getMessage());
      throw new ClientException(exceptionMsg, e);
    }
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addResourceFromClasspath(
      final String classpathResource) {
    ensureNotNull("classpath resource", classpathResource);

    try (InputStream resourceStream =
        getClass().getClassLoader().getResourceAsStream(classpathResource)) {
      if (resourceStream != null) {
        return addResourceStream(resourceStream, classpathResource);
      } else {
        throw new FileNotFoundException(classpathResource);
      }

    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy resource from classpath. %s", e.getMessage());
      throw new RuntimeException(exceptionMsg, e);
    }
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addResourceFile(final String filename) {
    ensureNotNull("filename", filename);

    try (InputStream resourceStream = new FileInputStream(filename)) {
      return addResourceStream(resourceStream, filename);
    } catch (final IOException e) {
      final String exceptionMsg =
          String.format("Cannot deploy resource from file. %s", e.getMessage());
      throw new RuntimeException(exceptionMsg, e);
    }
  }

  @Override
  public DeployWorkflowCommandBuilderStep2 addWorkflowModel(
      final BpmnModelInstance workflowDefinition, final String resourceName) {
    ensureNotNull("workflow model", workflowDefinition);

    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, workflowDefinition);
    return addResourceBytes(outStream.toByteArray(), resourceName);
  }

  @Override
  public FinalCommandStep<DeploymentEvent> requestTimeout(Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<DeploymentEvent> send() {
    final ZeebeClientFutureImpl<DeploymentEvent, DeployWorkflowResponse> future =
        new ZeebeClientFutureImpl<>(DeploymentEventImpl::new);

    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .deployWorkflow(request.build(), future);
    return future;
  }
}
