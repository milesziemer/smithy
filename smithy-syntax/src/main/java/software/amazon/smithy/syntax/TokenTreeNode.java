/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.syntax;

import software.amazon.smithy.model.node.ArrayNode;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.ToNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

class TokenTreeNode implements TokenTree {

    private final TreeType treeType;
    private final List<TokenTree> children = new ArrayList<>();
    private TokenTree parent;
    private List<CapturedToken> tokenCache;

    TokenTreeNode(TreeType treeType, List<CapturedToken> tokens) {
        this.treeType = treeType;
        tokens.forEach(this::appendChild);
    }

    @Override
    public final TokenTree getParent() {
        return parent;
    }

    @Override
    public final void setParent(TokenTree parent) {
        this.parent = parent;
        clearCaches();
    }

    @Override
    public final void clearCaches() {
        tokenCache = null;
        if (parent != null) {
            parent.clearCaches();
        }
    }

    @Override
    public final TreeType getType() {
        return treeType;
    }

    @Override
    public final List<CapturedToken> getTokens() {
        List<CapturedToken> captures = tokenCache;
        if (captures == null) {
            captures = new ArrayList<>();
            for (TokenTree tree : children) {
                captures.addAll(tree.getTokens());
            }
            tokenCache = captures;
        }
        return Collections.unmodifiableList(captures);
    }

    @Override
    public final List<TokenTree> getChildren() {
        return children;
    }

    @Override
    public final void appendChild(TokenTree tree) {
        children.add(tree);
        tree.setParent(this);
    }

    @Override
    public boolean removeChild(TokenTree tree) {
        clearCaches();
        return children.removeIf(c -> {
            if (c == tree) {
                c.setParent(null);
                return true;
            }
            return false;
        });
    }

    @Override
    public final String toString() {
        StringBuilder result = new StringBuilder();
        result.append(getType())
                .append(" (").append(getStartLine()).append(", ").append(getStartColumn())
                .append(") - (")
                .append(getEndLine()).append(", ").append(getEndColumn())
                .append(") {")
                .append('\n');
        if (getError() != null) {
            result.append("    ").append(getError()).append("\n    ---\n");
        }
        for (TokenTree child : children) {
            result.append("    ").append(child.toString().replace("\n", "\n    ")).append('\n');
        }
        result.append('}');
        return result.toString();
    }

    @Override
    public final int getStartPosition() {
        return children.isEmpty() ? 0 : children.get(0).getStartPosition();
    }

    @Override
    public final int getStartLine() {
        return children.isEmpty() ? 0 : children.get(0).getStartLine();
    }

    @Override
    public final int getStartColumn() {
        return children.isEmpty() ? 0 : children.get(0).getStartColumn();
    }

    @Override
    public final int getEndLine() {
        return children.isEmpty() ? getStartLine() : getLastToken().getEndLine();
    }

    @Override
    public final int getEndColumn() {
        return children.isEmpty() ? getStartColumn() : getLastToken().getEndColumn();
    }

    private CapturedToken getLastToken() {
        List<CapturedToken> capturedTokens = getTokens();
        return capturedTokens.get(capturedTokens.size() - 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        } else if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TokenTreeNode node = (TokenTreeNode) o;
        return treeType == node.treeType
               && getChildren().equals(node.getChildren())
               && Objects.equals(getParent(), node.getParent());
    }

    @Override
    public int hashCode() {
        return Objects.hash(treeType, getChildren(), getParent());
    }

    @Override
    public Node toNode() {
        ObjectNode.Builder nodeBuilder = Node.objectNodeBuilder();
        ArrayNode.Builder childrenNodesBuilder = ArrayNode.builder();
        for (TokenTree child: this.children) {
            childrenNodesBuilder.withValue(child.toNode());
        }
        return nodeBuilder.withMember(this.treeType.name().toLowerCase(), childrenNodesBuilder.build()).build();
    }
}
