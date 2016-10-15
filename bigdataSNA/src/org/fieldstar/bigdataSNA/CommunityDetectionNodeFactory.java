package org.fieldstar.bigdataSNA;

import org.knime.core.node.NodeDialogPane;
import org.knime.core.node.NodeFactory;
import org.knime.core.node.NodeView;

/**
 * <code>NodeFactory</code> for the "CommunityDetection" Node.
 * Input the SNA data, detect the community clusters.
 *
 * @author XIAOQING ZENG
 */
public class CommunityDetectionNodeFactory 
        extends NodeFactory<CommunityDetectionNodeModel> {

    /**
     * {@inheritDoc}
     */
    @Override
    public CommunityDetectionNodeModel createNodeModel() {
        return new CommunityDetectionNodeModel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNrNodeViews() {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeView<CommunityDetectionNodeModel> createNodeView(final int viewIndex,
            final CommunityDetectionNodeModel nodeModel) {
        return new CommunityDetectionNodeView(nodeModel);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasDialog() {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public NodeDialogPane createNodeDialogPane() {
        return new CommunityDetectionNodeDialog();
    }

}

