package org.treequery.Transform;

import lombok.Builder;
import org.treequery.model.Node;

@Builder
public class DelayNode  {
    private Node node;
    private int delaySeconds;
}
