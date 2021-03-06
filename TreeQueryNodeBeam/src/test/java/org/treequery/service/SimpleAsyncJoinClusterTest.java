package org.treequery.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.assertj.core.data.Offset;
import org.assertj.core.util.Sets;
import org.junit.jupiter.api.*;
import org.treequery.beam.cache.BeamCacheOutputBuilder;
import org.treequery.cluster.Cluster;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.discoveryservicestatic.proxy.LocalDummyDiscoveryServiceProxy;
import org.treequery.exception.CacheNotFoundException;
import org.treequery.service.proxy.LocalDummyTreeQueryClusterRunnerProxy;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.BasicAvroSchemaHelperImpl;
import org.treequery.model.CacheTypeEnum;
import org.treequery.model.Node;
import org.treequery.utils.*;
import org.treequery.utils.proxy.LocalCacheInputInterfaceProxyFactory;
import org.treequery.beam.cache.CacheInputInterface;
import org.treequery.utils.proxy.CacheInputInterfaceProxyFactory;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Slf4j
public class SimpleAsyncJoinClusterTest {
    TreeQueryClusterService treeQueryClusterService = null;

    CacheTypeEnum cacheTypeEnum;
    AvroSchemaHelper avroSchemaHelper = null;
    static DiscoveryServiceInterface discoveryServiceInterface = null;

    TreeQuerySetting treeQuerySetting = null;

    final static int PORT = 9002;//ThreadLocalRandom.current().nextInt(9000,9999);
    final static String HOSTNAME = "localhost";
    TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface;
    CacheInputInterface cacheInputInterface;

    @BeforeAll
    public static void staticinit(){
        discoveryServiceInterface = new LocalDummyDiscoveryServiceProxy();
    }

    @BeforeEach
    public void init() throws IOException {
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

                                                            TreeQuerySetting remoteDummyTreeQuerySetting = new TreeQuerySetting.TreeQuerySettingBuilder(
                                                                    _Cluster.getClusterName(),
                                                                    treeQuerySetting.getServicehostname(),
                                                                    treeQuerySetting.getServicePort(),
                                                                    treeQuerySetting.getCacheFilePath(),
                                                                    treeQuerySetting.getRedisHostName(),
                                                                    treeQuerySetting.getRedisPort(),
                                                                    treeQuerySetting.getServiceDiscoveryHostName(),
                                                                    treeQuerySetting.getServiceDiscoveryPort()
                                                            ).build();

                                                            CacheInputInterface _CacheInputInterface = cacheInputInterfaceProxyFactory.getDefaultCacheInterface(remoteDummyTreeQuerySetting, discoveryServiceInterface);


                                                            return TreeQueryClusterRunnerImpl.builder()
                                                                    .beamCacheOutputBuilder(BeamCacheOutputBuilder.builder()
                                                                            .treeQuerySetting(treeQuerySetting)
                                                                            .build())
                                                                    .avroSchemaHelper(avroSchemaHelper)
                                                                    .treeQuerySetting(remoteDummyTreeQuerySetting)
                                                                    .discoveryServiceInterface(discoveryServiceInterface)
                                                                    .cacheInputInterface(_CacheInputInterface)
                                                                    .build();
                                                        }
                                                )
                                                .build();
    }
    @RepeatedTest(1)
    public void SimpleAsyncJoinTestWithSameCluster() throws Exception{
        String AvroTree = "SimpleJoin.json";
        this.runTest(AvroTree, checkSimpleJoinCriteria(treeQuerySetting));
    }
    @RepeatedTest(1)
    public void SimpleAsyncJoinTestWithDiffCluster() throws Exception{
        String AvroTree = "SimpleJoinB.json";
        this.runTest(AvroTree,checkSimpleJoinCriteria(treeQuerySetting));
    }

    @Test
    public void SimpleAsyncJoinTestWithMixedCluster() throws Exception{
        String AvroTree = "SimpleJoinCluster.json";
        this.runTest(AvroTree, checkSimpleJoinCriteria(treeQuerySetting));
    }

    @Test
    public void AsyncJoinTest4layers() throws Exception{
        String AvroTree = "TreeQueryInput4.json";
        this.runTest(AvroTree,
                (rootNode)->{
                    long pageSize = 10000;
                    long page = 1;
                    AtomicInteger counter = new AtomicInteger();
                    Set<GenericRecord> genericRecordSet = Sets.newHashSet();
                    try {
                        Schema schema = AvroIOHelper.getPageRecordFromAvroCache(
                                treeQuerySetting,
                                rootNode.getIdentifier(), pageSize, page,
                                (record) -> {
                                    assertThat(record).isNotNull();
                                    String bondTradeTenor = GenericRecordSchemaHelper.StringifyAvroValue(record, "bondtrade_bondstatic.bondstatic.original_maturity");
                                    String bondMarketDataTenor = GenericRecordSchemaHelper.StringifyAvroValue(record, "bondprice.Tenor");
                                    assertEquals(bondTradeTenor, bondMarketDataTenor);
                                    GenericRecordSchemaHelper.DoubleField doubleField = new GenericRecordSchemaHelper.DoubleField();
                                    GenericRecordSchemaHelper.getValue(record, "bondprice.Price", doubleField);
                                    double refPrice=0;
                                    if (bondTradeTenor.equals("10Y")){
                                        refPrice = 0.72;
                                    }else if(bondMarketDataTenor.equals("15Y")){
                                        refPrice = 0.78;
                                    }else if(bondMarketDataTenor.equals("5Y")){
                                        refPrice = 0.6;
                                    }else if(bondMarketDataTenor.equals("3Y")){
                                        refPrice = 0.62;
                                    }
                                    assertThat(doubleField.getValue()).isCloseTo(refPrice, Offset.offset(0.0001));
                                    counter.incrementAndGet();
                                    genericRecordSet.add(record);

                                });
                    }catch (CacheNotFoundException che){
                        che.printStackTrace();
                        throw new IllegalStateException(che.getMessage());
                    }
                    assertEquals(3000, genericRecordSet.size());
                    assertEquals(3000, counter.get());
                });
    }

    @Disabled
    @Test
    public void checkIdentifier() throws Exception{
        String AvroTree = "SimpleJoinC.json";
        String jsonString = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree);
        Node rootNode = JsonInstructionHelper.createNode(jsonString);
        String AvroTree2 = "SimpleJoinB.json";
        String jsonString2 = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree2);
        Node rootNode2 = JsonInstructionHelper.createNode(jsonString2);
        assertNotEquals(jsonString, jsonString2);
        assertNotEquals(rootNode.getIdentifier(), rootNode2.getIdentifier());
    }

    private static Consumer<Node> checkSimpleJoinCriteria(TreeQuerySetting treeQuerySetting){
        return (rootNode)->{
            long pageSize = 10000;
            long page = 1;
            AtomicInteger counter = new AtomicInteger();
            Set<GenericRecord> genericRecordSet = Sets.newHashSet();
            try {
                Schema schema = AvroIOHelper.getPageRecordFromAvroCache(
                        treeQuerySetting,
                        rootNode.getIdentifier(), pageSize, page,
                        (record) -> {
                            assertThat(record).isNotNull();
                            counter.incrementAndGet();
                            String isinBondTrade = GenericRecordSchemaHelper.StringifyAvroValue(record, "bondtrade.asset.securityId");
                            String isinSecCode = GenericRecordSchemaHelper.StringifyAvroValue(record, "bondstatic.isin_code");
                            assertThat(genericRecordSet).doesNotContain(record);
                            assertEquals(isinBondTrade, isinSecCode);
                            assertThat(isinBondTrade.length()).isGreaterThan(5);
                            genericRecordSet.add(record);
                        });
            }catch (CacheNotFoundException che){
                che.printStackTrace();
                throw new IllegalStateException(che.getMessage());
            }
            assertEquals(1000, genericRecordSet.size());
            assertEquals(1000, counter.get());
        };
    }

    private void runTest(String AvroTree, Consumer<Node> testValidation) throws Exception{
        String jsonString = TestDataAgent.prepareNodeFromJsonInstruction(AvroTree);
        Node rootNode = JsonInstructionHelper.createNode(jsonString);
        //assertThat(rootNode).isInstanceOf(JoinNode.class);
        log.debug("Run for data Identifier:"+ rootNode.getIdentifier());
        treeQueryClusterService =  BatchAsyncTreeQueryClusterService.builder()
                .treeQueryClusterRunnerFactory(()->{
                    return TreeQueryClusterRunnerImpl.builder()
                            .beamCacheOutputBuilder(BeamCacheOutputBuilder.builder()
                                    .treeQuerySetting(this.treeQuerySetting)
                                    .build())
                            .avroSchemaHelper(avroSchemaHelper)
                            .treeQuerySetting(treeQuerySetting)
                            .treeQueryClusterRunnerProxyInterface(treeQueryClusterRunnerProxyInterface)
                            .cacheInputInterface(cacheInputInterface)
                            .discoveryServiceInterface(discoveryServiceInterface)
                            .build();
                })
                .build();
        final AsyncRunHelper asyncRunHelper =  AsyncRunHelper.create();
        treeQueryClusterService.runQueryTreeNetwork(rootNode, (status)->{
            log.debug(status.toString());

            boolean IsIssue = status.status!= StatusTreeQueryCluster.QueryTypeEnum.SUCCESS;

            if (IsIssue || status.getNode().getIdentifier().equals(rootNode.getIdentifier()))
                asyncRunHelper.continueRun(status);

            if(IsIssue)
                throw new IllegalStateException(status.toString());

        });
        StatusTreeQueryCluster statusTreeQueryCluster = asyncRunHelper.waitFor();
        if (statusTreeQueryCluster.getStatus() != StatusTreeQueryCluster.QueryTypeEnum.SUCCESS){
            throw new RuntimeException(statusTreeQueryCluster.getDescription());
        }

        log.debug("Retrieve result now");
        //Check the avro file
        String identifier = rootNode.getIdentifier();
        log.debug("Look for data Identifier:"+identifier+"from:"+discoveryServiceInterface.toString());
        Cluster getCluster = Optional.ofNullable(discoveryServiceInterface.getCacheResultCluster(identifier))
                            .orElseThrow(()->{
                                return new RuntimeException("No cluster found for "+identifier+" map: "+discoveryServiceInterface.toString());
                            });

        assertThat(getCluster)
                .isEqualTo(rootNode.getCluster());


        testValidation.accept(rootNode);
    }
}
