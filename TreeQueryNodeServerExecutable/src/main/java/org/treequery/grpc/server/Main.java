package org.treequery.grpc.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;
import org.treequery.config.TreeQuerySetting;
import org.treequery.discoveryservicestatic.DiscoveryServiceInterface;
import org.treequery.discoveryservicestatic.client.ServiceDiscoveryClient;
import org.treequery.grpc.utils.WebServerFactory;
import org.treequery.proto.TreeQueryRequest;
import org.treequery.service.proxy.GrpcTreeQueryClusterRunnerProxy;
import org.treequery.service.proxy.TreeQueryClusterRunnerProxyInterface;
import org.treequery.utils.DatabaseSettingHelper;
import org.treequery.utils.TreeQuerySettingHelper;


import java.io.IOException;

@Slf4j
public class Main {

    static TreeQueryRequest.RunMode RUNMODE = TreeQueryRequest.RunMode.DIRECT;
    static boolean RENEW_CACHE = true;


    private static DiscoveryServiceInterface getDiscoveryServiceProxy(String hostname, int port){
        String URL = String.format("http://%s:%d", hostname, port);
        DiscoveryServiceInterface discoveryServiceInterface =
                new ServiceDiscoveryClient(URL);

        return discoveryServiceInterface;
    }

    private static TreeQueryClusterRunnerProxyInterface createRemoteProxy(DiscoveryServiceInterface discoveryServiceInterface){
        return GrpcTreeQueryClusterRunnerProxy.builder()
                .discoveryServiceInterface(discoveryServiceInterface)
                .runMode(RUNMODE)
                .renewCache(RENEW_CACHE)
                .build();
    }
    private static WebServer startTreeQueryServer(String treeQueryYaml){
        TreeQuerySetting treeQuerySetting = TreeQuerySettingHelper.createFromYaml(treeQueryYaml, true);
        log.info("Run with following configuration:" + treeQuerySetting.toString());
        DiscoveryServiceInterface discoveryServiceInterface = getDiscoveryServiceProxy(
                treeQuerySetting.getServiceDiscoveryHostName(),
                treeQuerySetting.getServiceDiscoveryPort()
        );
        TreeQueryClusterRunnerProxyInterface treeQueryClusterRunnerProxyInterface = createRemoteProxy(discoveryServiceInterface);


        WebServer webServer = WebServerFactory.createWebServer(
                treeQuerySetting,
                discoveryServiceInterface,
                treeQueryClusterRunnerProxyInterface
        );
        discoveryServiceInterface.registerCluster(treeQuerySetting.getCluster(),
                treeQuerySetting.getServicehostname(),
                treeQuerySetting.getServicePort());

        return webServer;
    }

    public static void main(String [] args) throws IOException, InterruptedException {
        log.info("Working Directory = " + System.getProperty("user.dir"));
        Options options = new Options();
        Option clusterInput = new Option("c", "cluster", true, "cluster setting");
        clusterInput.setRequired(true);
        options.addOption(clusterInput);

        Option databaseConn = new Option("d", "database", true, "database setting");
        databaseConn.setRequired(true);
        options.addOption(databaseConn);

        CommandLineParser parser = new DefaultParser();
        HelpFormatter formatter = new HelpFormatter();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            log.error(e.getMessage());
            formatter.printHelp("utility-name", options);
            System.exit(1);
        }
        String clusterYaml = cmd.getOptionValue("cluster");
        String databaseYaml = cmd.getOptionValue("database");
        WebServer webServer = startTreeQueryServer(clusterYaml);
        DatabaseSettingHelper.initDatabaseSettingHelper(databaseYaml, true, true);
        webServer.start();
        webServer.blockUntilShutdown();
    }


}
