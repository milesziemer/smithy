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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Consumer;
import software.amazon.smithy.model.loader.IdlToken;
import software.amazon.smithy.model.loader.IdlTokenizer;
import software.amazon.smithy.model.loader.ModelSyntaxException;
import software.amazon.smithy.model.shapes.ShapeType;

final class TokenTreeBuilder {

    private final IdlTokenizer tokenizer;
    private final TokenTree root = TokenTree.of(TreeType.IDL);
    private final Deque<TokenTree> trees = new ArrayDeque<>();
    private final Deque<CapturedToken> danglingTokens = new ArrayDeque<>();

    TokenTreeBuilder(IdlTokenizer tokenizer) {
        this.tokenizer = tokenizer;
        trees.add(root);
    }

    TokenTree create() {
        parseControlSection();
        parseMetadataSection();
        parseShapeSection();
        return root;
    }

    private void defaultErrorRecovery() {
        while (tokenizer.hasNext()) {
            if (tokenizer.getCurrentTokenColumn() == 1) {
                IdlToken token = tokenizer.getCurrentToken();
                if (token == IdlToken.DOLLAR
                    || token == IdlToken.IDENTIFIER
                    || token == IdlToken.DOC_COMMENT
                    || token == IdlToken.AT) {
                    return;
                }
            }
            appendAndNext();
        }
    }

    private TokenTree withState(TreeType state, Runnable parser) {
        return withState(state, this::defaultErrorRecovery, parser);
    }

    private TokenTree withState(TreeType state, Runnable errorRecovery, Runnable parser) {
        return withState(state, errorRecovery, tree -> parser.run());
    }

    private TokenTree withState(TreeType state, Consumer<TokenTree> parser) {
        return withState(state, this::defaultErrorRecovery, parser);
    }

    private TokenTree withState(TreeType state, Runnable errorRecovery, Consumer<TokenTree> parser) {
        TokenTree tree = TokenTree.of(state);
        trees.getFirst().appendChild(tree);
        trees.addFirst(tree);
        try {
            parser.accept(tree);
        } catch (ModelSyntaxException e) {
            withState(TreeType.ERROR, errorTree -> {
                errorTree.appendChild(TokenTree.of(e, tokenizer));
                tokenizer.next();
                errorRecovery.run();
            });
        }
        trees.removeFirst();
        return tree;
    }

    private void skipWhitespace() {
        TokenTree tree = trees.getFirst();
        while (tokenizer.getCurrentToken().isWhitespace()) {
            tree.appendChild(CapturedToken.from(tokenizer));
            tokenizer.next();
        }
    }

    private void skipWhitespaceAndDocs() {
        TokenTree tree = trees.getFirst();
        while (tokenizer.getCurrentToken().isWhitespace() || tokenizer.getCurrentToken() == IdlToken.DOC_COMMENT) {
            tree.appendChild(CapturedToken.from(tokenizer));
            tokenizer.next();
        }
    }

    private void skipSpaces() {
        TokenTree tree = trees.getFirst();
        while (tokenizer.getCurrentToken() == IdlToken.SPACE) {
            tree.appendChild(CapturedToken.from(tokenizer));
            tokenizer.next();
        }
    }

    private void expectAndSkipBr() {
        withState(TreeType.BR, subtree -> {
            tokenizer.skipSpaces(); //todo: also skip commas

            if (tokenizer.getCurrentToken() == IdlToken.NEWLINE) {
                skipWhitespace();
            } else {
                int line = tokenizer.getLine();
                skipWhitespace();
                if (line == tokenizer.getLine() && tokenizer.hasNext()) {
                    throw new ModelSyntaxException("Expected newline", tokenizer.getCurrentTokenLocation());
                }
            }
        });

        //tokenizer.removePendingDocCommentLines();
    }

    void appendAndNext() {
        trees.getFirst().appendChild(CapturedToken.from(tokenizer));
        tokenizer.next();
    }

    void expectAndAppendAndNext(IdlToken... tokens) {
        tokenizer.expect(tokens);
        trees.getFirst().appendChild(CapturedToken.from(tokenizer));
        tokenizer.next();
    }

    void expectAndSkipSpaces() {
        expectAndAppendAndNext(IdlToken.SPACE);
        skipSpaces();
    }

    private void parseControlSection() {
        withState(TreeType.CONTROL_SECTION, () -> {
            skipWhitespaceAndDocs();
            while (tokenizer.getCurrentToken() == IdlToken.DOLLAR) {
                withState(TreeType.CONTROL_STATEMENT, controlStatement -> {
                    appendAndNext(); //  append '$'
                    // Capture the key and any key-specific errors.
                    withState(TreeType.KEY, keyTree -> {
                        expectAndAppendAndNext(IdlToken.IDENTIFIER, IdlToken.STRING);
                    });
                    skipSpaces();
                    expectAndAppendAndNext(IdlToken.COLON);
                    skipSpaces();
                    withState(TreeType.VALUE, this::parseNode);
                    expectAndSkipBr();
                });
            }
        });
    }

    private void parseMetadataSection() {
        withState(TreeType.METADATA_SECTION, () -> {
            skipWhitespaceAndDocs();
            while (tokenizer.doesCurrentIdentifierStartWith('m')) {
                withState(TreeType.METADATA_STATEMENT, () -> {
                    withState(TreeType.KEYWORD, () -> {
                        tokenizer.expectCurrentLexeme("metadata");
                        appendAndNext(); //  append 'metadata'
                    });
                    skipSpaces();
                    // Capture the key and any key-specific errors.
                    withState(TreeType.KEY, keyTree -> {
                        expectAndAppendAndNext(IdlToken.IDENTIFIER, IdlToken.STRING);
                    });
                    skipSpaces();
                    expectAndAppendAndNext(IdlToken.EQUAL);
                    skipSpaces();
                    withState(TreeType.VALUE, this::parseNode);
                    expectAndSkipBr();
                });
            }
        });
    }

    private void parseShapeSection() {
        withState(TreeType.SHAPE_SECTION, () -> {
            if (tokenizer.doesCurrentIdentifierStartWith('n')) {

                withState(TreeType.SHAPE_SECTION_NAMESPACE, () -> {
                    withState(TreeType.KEYWORD, () -> {
                        tokenizer.expectCurrentLexeme("namespace");
                        appendAndNext(); // skip "namespace"
                    });
                    expectAndSkipSpaces();
                    withState(TreeType.VALUE, this::skipShapeIdNamespace);
                    expectAndSkipBr();
                });

                skipWhitespaceAndDocs();
                parseUseSection();
                skipWhitespaceAndDocs();

                withState(TreeType.SHAPE_STATEMENTS, tree -> {
                    while (tokenizer.getCurrentToken() == IdlToken.IDENTIFIER) {
                        while (!danglingTokens.isEmpty()) {
                            tree.appendChild(danglingTokens.removeFirst());
                        }
                        String keyword = tokenizer.internString(tokenizer.getCurrentTokenLexeme());
                        if (keyword.equals("apply")) {
                            throw new UnsupportedOperationException();
                        }
                        ShapeType type = ShapeType.fromString(keyword)
                                .orElseThrow(() -> new ModelSyntaxException("Expected a valid shape type",
                                                                            tokenizer.getCurrentTokenLocation()));
                        withState(TreeType.SHAPE_STATEMENT, () -> {
                            withState(TreeType.KEYWORD, this::appendAndNext);
                            skipSpaces();
                            withState(TreeType.SHAPE_NAME, () -> {
                                skipRelativeRootShapeId(false);
                            });
                            expectAndSkipBr();
                        });
                    }
                });

                // parseFirstShapeStatement(possibleDocCommentLocation);
                // parseSubsequentShapeStatements();
            } else if (tokenizer.hasNext()) {
                throw new ModelSyntaxException(
                        "Expected a namespace definition but found "
                        + tokenizer.getCurrentToken().getDebug(tokenizer.getCurrentTokenLexeme()),
                        tokenizer.getCurrentTokenLocation());
            }
        });
    }

    private void parseUseSection() {
        TokenTree tree = withState(TreeType.USE_SECTION, () -> {
            while (tokenizer.getCurrentToken() == IdlToken.IDENTIFIER) {
                // Don't over-parse here for unions.
                String keyword = tokenizer.internString(tokenizer.getCurrentTokenLexeme());
                if (!keyword.equals("use")) {
                    break;
                }
                withState(TreeType.USE_STATEMENT, () -> {
                    withState(TreeType.KEYWORD, this::appendAndNext);
                    expectAndSkipSpaces();
                    withState(TreeType.VALUE, () -> {
                        expectAndSkipAbsoluteShapeId(false);
                    });
                    expectAndSkipBr();
                    skipWhitespaceAndDocs();
                });
            }
        });

        // Detect if we over-captured documentation comments that belong on the first parsed shape.
        if (!tree.getChildren().isEmpty()) {
            List<TokenTree> chidren = tree.getChildren();
            ListIterator<TokenTree> childIter = chidren.listIterator(chidren.size());
            while (childIter.hasPrevious()) {
                TokenTree child = childIter.previous();
                if (isWhitespaceTree(child) || isDocTree(child)) {
                    //danglingTokens.addAll(child.getTokens());
                    //childIter.remove();
                } else {
                    break;
                }
            }
        }
    }

    private boolean isWhitespaceTree(TokenTree tree) {
        if (tree.getChildren().size() == 1) {
            List<CapturedToken> tokens = tree.getChildren().get(0).getTokens();
            if (tokens.size() == 1) {
                CapturedToken token = tokens.get(0);
                return token.getToken().isWhitespace() || token.getToken() == IdlToken.COMMA;
            }
        }
        return false;
    }

    private boolean isDocTree(TokenTree tree) {
        if (tree.getChildren().size() == 1) {
            List<CapturedToken> tokens = tree.getChildren().get(0).getTokens();
            if (tokens.size() == 1) {
                return tokens.get(0).getToken() == IdlToken.DOC_COMMENT;
            }
        }
        return false;
    }

    private void skipShapeIdNamespace() {
        withState(TreeType.ID_NAMESPACE, () -> {
            expectAndAppendAndNext(IdlToken.IDENTIFIER);
            while (tokenizer.getCurrentToken() == IdlToken.DOT) {
                appendAndNext();
                expectAndAppendAndNext(IdlToken.IDENTIFIER);
            }
        });
    }

    private void skipRelativeRootShapeId(boolean allowMember) {
        withState(TreeType.ID_NAME, () -> {
            expectAndAppendAndNext(IdlToken.IDENTIFIER);
        });

        // Parse member if allowed and present.
        if (allowMember && tokenizer.getCurrentToken() == IdlToken.DOLLAR) {
            appendAndNext(); // skip '$'
            withState(TreeType.ID_MEMBER, () -> {
                expectAndAppendAndNext(IdlToken.IDENTIFIER);
            });
        }
    }

    void expectAndSkipAbsoluteShapeId(boolean allowMember) {
        withState(TreeType.REFERENCE, () -> {
            skipShapeIdNamespace();
            expectAndAppendAndNext(IdlToken.POUND);
            skipRelativeRootShapeId(allowMember);
        });
    }

    private void parseNode() {
        withState(TreeType.NODE_VALUE, () -> {
            IdlToken token = tokenizer.expect(IdlToken.STRING, IdlToken.TEXT_BLOCK, IdlToken.NUMBER,
                                              IdlToken.IDENTIFIER, IdlToken.LBRACE, IdlToken.LBRACKET);
            switch (token) {
                case STRING:
                case TEXT_BLOCK:
                case IDENTIFIER:
                case NUMBER:
                    appendAndNext();
                    break;
                case LBRACE:
                    throw new UnsupportedOperationException("[");
                case LBRACKET:
                default:
                    throw new UnsupportedOperationException("{");
            }
        });
    }
}
