/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.partitions;

import static io.zeebe.broker.clustering.base.partitions.Partition.getPartitionName;
import static io.zeebe.broker.clustering.base.partitions.PartitionServiceNames.partitionInstallServiceName;

import io.atomix.cluster.MemberId;
import io.atomix.core.Atomix;
import io.atomix.protocols.raft.partition.RaftPartition;
import io.atomix.protocols.raft.partition.RaftPartitionGroup;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.distributedlog.StorageConfiguration;
import io.zeebe.distributedlog.StorageConfigurationManager;
import io.zeebe.servicecontainer.Injector;
import io.zeebe.servicecontainer.Service;
import io.zeebe.servicecontainer.ServiceName;
import io.zeebe.servicecontainer.ServiceStartContext;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Always installed on broker startup: reads configuration of all locally available partitions and
 * starts the corresponding services (logstream, partition ...)
 */
public class BootstrapPartitions implements Service<Void> {
  private final Injector<StorageConfigurationManager> configurationManagerInjector =
      new Injector<>();
  private final BrokerCfg brokerCfg;

  private StorageConfigurationManager configurationManager;
  private ServiceStartContext startContext;

  private final Injector<Atomix> atomixInjector = new Injector<>();
  private Atomix atomix;

  public BootstrapPartitions(final BrokerCfg brokerCfg) {
    this.brokerCfg = brokerCfg;
  }

  @Override
  public void start(final ServiceStartContext startContext) {
    configurationManager = configurationManagerInjector.getValue();
    atomix = atomixInjector.getValue();

    final RaftPartitionGroup partitionGroup =
        (RaftPartitionGroup) atomix.getPartitionService().getPartitionGroup(Partition.GROUP_NAME);

    final MemberId nodeId = atomix.getMembershipService().getLocalMember().id();
    final List<RaftPartition> owningPartitions =
        partitionGroup.getPartitions().stream()
            .filter(partition -> partition.members().contains(nodeId))
            .map(RaftPartition.class::cast)
            .collect(Collectors.toList());

    this.startContext = startContext;
    startContext.run(
        () -> {
          for (RaftPartition owningPartition : owningPartitions) {
            installPartition(owningPartition);
          }
        });
  }

  private void installPartition(RaftPartition partition) {
    final StorageConfiguration configuration =
        configurationManager.createConfiguration(partition.id().id()).join();
    installPartition(startContext, configuration, partition);
  }

  private void installPartition(
      final ServiceStartContext startContext,
      final StorageConfiguration configuration,
      RaftPartition partition) {
    final String partitionName = getPartitionName(configuration.getPartitionId());
    final ServiceName<Void> partitionInstallServiceName =
        partitionInstallServiceName(partitionName);
    final String localMemberId = atomix.getMembershipService().getLocalMember().id().id();

    final PartitionInstallService partitionInstallService =
        new PartitionInstallService(
            partition,
            atomix.getEventService(),
            atomix.getCommunicationService(),
            configuration,
            brokerCfg);

    startContext.createService(partitionInstallServiceName, partitionInstallService).install();
  }

  @Override
  public Void get() {
    return null;
  }

  public Injector<StorageConfigurationManager> getConfigurationManagerInjector() {
    return configurationManagerInjector;
  }

  public Injector<Atomix> getAtomixInjector() {
    return this.atomixInjector;
  }
}
