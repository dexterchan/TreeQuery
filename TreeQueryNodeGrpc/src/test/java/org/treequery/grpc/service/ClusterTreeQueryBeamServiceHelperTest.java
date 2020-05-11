package org.treequery.grpc.service;

import com.google.common.collect.Lists;
import lombok.Getter;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.treequery.beam.cache.BeamCacheOutputBuilder;
import org.treequery.beam.cache.CacheInputInterface;
import org.treequery.cluster.Cluster;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservice.DiscoveryServiceInterface;
import org.treequery.discoveryservice.proxy.DiscoveryServiceProxyImpl;
import org.treequery.grpc.utils.TestDataAgent;
import org.treequery.model.CacheTypeEnum;
import org.treequery.proto.TreeQueryRequest;
import org.treequery.service.PreprocessInput;
import org.treequery.service.ReturnResult;
import org.treequery.service.StatusTreeQueryCluster;
import org.treequery.service.TreeQueryClusterRunnerImpl;
import org.treequery.service.proxy.LocalDummyTreeQueryClusterRunnerProxy;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.*;
import org.treequery.utils.proxy.CacheInputInterfaceProxyFactory;
import org.treequery.utils.proxy.LocalCacheInputInterfaceProxyFactory;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
public class ClusterTreeQueryBeamServiceHelperTest {
    final static int PORT = 9009;//ThreadLocalRandom.current().nextInt(9000,9999);
    final static String HOSTNAME = "localhost";
    String jsonString;
    static DiscoveryServiceInterface discoveryServiceInterface = null;
    CacheTypeEnum cacheTypeEnum;
    TreeQueryBeamServiceHelper treeQueryBeamServiceHelper;
    AvroSchemaHelper avroSchemaHelper;
    TreeQuerySetting treeQuerySetting;
    TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface;
    CacheInputInterface cacheInputInterface;

    @BeforeAll
    public static void staticinit(){
        discoveryServiceInterface = new DiscoveryServiceProxyImpl();
    }

    @BeforeEach
    public void init() throws IOException {
        String AvroTree = "SimpleJoinCluster.json";
        jsonString = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree);

        cacheTypeEnum = CacheTypeEnum.FILE;
        treeQuerySetting = TreeQuerySettingHelper.createFromYaml();
        avroSchemaHelper = new BasicAvroSchemaHelperImpl();

        Cluster clusterA = Cluster.builder().clusterName("A").build();
        Cluster clusterB = Cluster.builder().clusterName("B").build();
        discoveryServiceInterface.registerCluster(clusterA, HOSTNAME, PORT);
        discoveryServiceInterface.registerCluster(clusterB, HOSTNAME, PORT);
        CacheInputInterfaceProxyFactory cacheInputInterfaceProxyFactory = new LocalCacheInputInterfaceProxyFactory();
        cacheInputInterface = cacheInputInterfaceProxyFactory.getDefaultCacheInterface(treeQuerySetting, discoveryServiceInterface);


        treeQueryClusterRunnerProxyInterface = LocalDummyTreeQueryClusterRunnerProxy.builder()
                .treeQuerySetting(treeQuerySetting)
                .avroSchemaHelper(avroSchemaHelper)
                .createLocalTreeQueryClusterRunnerFunc(
                        (_Cluster)-> {
                            TreeQuerySetting remoteDummyTreeQuerySetting = new TreeQuerySetting(
                                    _Cluster.getClusterName(),
                                    treeQuerySetting.getServicehostname(),
                                    treeQuerySetting.getServicePort(),
                                    treeQuerySetting.getCacheFilePath(),
                                    treeQuerySetting.getRedisHostName(),
                                    treeQuerySetting.getRedisPort()
                            );

                            return TreeQueryClusterRunnerImpl.builder()
                                    .beamCacheOutputBuilder(BeamCacheOutputBuilder.builder()
                                            .treeQuerySetting(treeQuerySetting)
                                            .build())
                                    .avroSchemaHelper(avroSchemaHelper)
                                    .treeQuerySetting(remoteDummyTreeQuerySetting)
                                    .discoveryServiceInterface(discoveryServiceInterface)
                                    .cacheInputInterface(cacheInputInterface)
                                    .build();
                        }
                )
                .build();
        treeQueryBeamServiceHelper = TreeQueryBeamServiceHelper.builder()
                .avroSchemaHelper(avroSchemaHelper)
                .discoveryServiceInterface(discoveryServiceInterface)
                .treeQuerySetting(treeQuerySetting)
                .treeQueryClusterRunnerProxyInterface(treeQueryClusterRunnerProxyInterface)
                .cacheInputInterface(cacheInputInterface)
                .build();


    }
    @Test
    void throwIllegalArugmentExceptionIfBlankProxy(){
        treeQueryBeamServiceHelper = TreeQueryBeamServiceHelper.builder()
                .avroSchemaHelper(avroSchemaHelper)
                .discoveryServiceInterface(discoveryServiceInterface)
                .cacheInputInterface(cacheInputInterface)
                .treeQuerySetting(treeQuerySetting)
                .build();
        int pageSize = 3;
        DataConsumer2LinkedList genericRecordConsumer = new DataConsumer2LinkedList();
        PreprocessInput preprocessInput = treeQueryBeamServiceHelper.preprocess(jsonString);

        // assertThrows(IllegalStateException.class,
        //()->{
        ReturnResult returnResult = treeQueryBeamServiceHelper.runAndPageResult(TreeQueryRequest.RunMode.DIRECT,
                preprocessInput,
                true,
                pageSize,
                2,
                genericRecordConsumer);
        //}
        //);
        assertEquals(StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR,returnResult.getStatusTreeQueryCluster().getStatus());

    }

    @Test
    void happyPathRunBeamJoinLocally() throws Exception{
        //TreeQueryRequest treeQueryRequest =  TreeQueryRequest.
        int pageSize = 3;
        DataConsumer2LinkedList genericRecordConsumer = new DataConsumer2LinkedList();
        PreprocessInput preprocessInput = treeQueryBeamServiceHelper.preprocess(jsonString);

        ReturnResult returnResult = treeQueryBeamServiceHelper.runAndPageResult(TreeQueryRequest.RunMode.DIRECT,
                preprocessInput,
                true,
                pageSize,
                2,
                genericRecordConsumer);

        assertThat(returnResult.getStatusTreeQueryCluster().getStatus()).isEqualTo(StatusTreeQueryCluster.QueryTypeEnum.SUCCESS);
        assertThat(genericRecordConsumer.getGenericRecordList()).hasSize(pageSize);
        genericRecordConsumer.getGenericRecordList().forEach(
                genericRecord -> {
                    //log.debug(genericRecord.toString());
                    assertThat(genericRecord.toString()).isNotBlank();
                }
        );


        pageSize = 10000;
        long page = 1;
        AtomicInteger counter = new AtomicInteger();
        Set<GenericRecord> genericRecordSet = Sets.newHashSet();
        Schema schema = AvroIOHelper.getPageRecordFromAvroCache(
                treeQuerySetting,
                preprocessInput.getNode().getIdentifier(),pageSize,page,
                (record)->{
                    assertThat(record).isNotNull();
                    counter.incrementAndGet();
                    String isinBondTrade = GenericRecordSchemaHelper.StringifyAvroValue(record, "bondtrade.asset.securityId");
                    String isinSecCode = GenericRecordSchemaHelper.StringifyAvroValue(record,"bondstatic.isin_code");
                    assertThat(genericRecordSet).doesNotContain(record);
                    assertEquals(isinBondTrade, isinSecCode);
                    assertThat(isinBondTrade.length()).isGreaterThan(5);
                    genericRecordSet.add(record);
                });

        assertEquals(1000, counter.get());

    }

    private static class DataConsumer2LinkedList implements Consumer<GenericRecord> {
        @Getter
        List<GenericRecord> genericRecordList = Lists.newLinkedList();
        @Override
        public void accept(GenericRecord genericRecord) {
            genericRecordList.add(genericRecord);
        }
    }
    private static class DataConsumer2Set implements Consumer<GenericRecord>{
        @Getter
        Set<GenericRecord> genericRecordSet = Sets.newHashSet();
        @Override
        public void accept(GenericRecord genericRecord) {
            genericRecordSet.add(genericRecord);
        }
    }
}
