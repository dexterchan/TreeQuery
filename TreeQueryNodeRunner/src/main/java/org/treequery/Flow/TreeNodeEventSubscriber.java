package org.treequery.Flow;

import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.treequery.dto.TreeNodeEvent;
import org.treequery.exception.FailClusterRunException;
import org.treequery.model.Node;
import org.treequery.service.StatusTreeQueryCluster;
import org.treequery.service.TreeQueryClusterRunner;
import org.treequery.utils.AppExceptionHandler;
import org.treequery.utils.EventBus.TreeNodeEventBusTrafficLight;

import java.util.UUID;
import java.util.concurrent.Flow;

@Slf4j
@Builder
public class TreeNodeEventSubscriber implements Flow.Subscriber<TreeNodeEvent>{
    private Flow.Subscription subscription;
    @NonNull final TreeQueryClusterRunner treeQueryClusterRunner;
    @NonNull final TreeNodeEventBusTrafficLight treeNodeEventBusTrafficLight;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        subscription.request(1);
    }

    @Override
    public void onNext(TreeNodeEvent item) {
        subscription.request(1);
        Node calcNode = item.getCalcNode();
        log.info(String.format("%s Execute Node:%s", treeQueryClusterRunner.getClass().toString(),calcNode ));

        //register
        try {
            this.treeQueryClusterRunner.runQueryTreeNetwork(
                    calcNode,
                    statusTreeQueryCluster -> {
                        final StatusTreeQueryCluster.QueryTypeEnum Status = statusTreeQueryCluster.getStatus();
                        if (Status == StatusTreeQueryCluster.QueryTypeEnum.FAIL ||
                                Status == StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR) {
                            item.getStatusCallback().accept(statusTreeQueryCluster);
                            throw new FailClusterRunException(statusTreeQueryCluster);
                        }
                        //Push the next waiting node
                        try {
                            treeNodeEventBusTrafficLight.unqueue(item.getId(), statusTreeQueryCluster);
                        } catch (Throwable ine) {
                            log.error(ine.getMessage());
                            String errMsg = "Not able to insert item to event bus:" + ine.getMessage();
                            AppExceptionHandler.feedBackException2Client(
                                    item.getStatusCallback(),
                                    calcNode,
                                    errMsg,
                                    StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR
                            );
                            throw new RuntimeException(errMsg);
                        }
                    }
            );
        }catch(Throwable th){
            AppExceptionHandler.feedBackException2Client(
                    item.getStatusCallback(),
                    calcNode,
                    th.getMessage(),
                    StatusTreeQueryCluster.QueryTypeEnum.SYSTEMERROR
            );
            throw new RuntimeException(th.getMessage());
        }
    }

    @Override
    public void onError(Throwable throwable) {
        log.error("Subscriber OnError:"+throwable.getMessage());
    }

    @Override
    public void onComplete() {
        log.info("last run completed");
    }
}
