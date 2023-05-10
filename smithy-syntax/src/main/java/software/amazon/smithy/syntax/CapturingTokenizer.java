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
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import software.amazon.smithy.model.loader.IdlToken;
import software.amazon.smithy.model.loader.IdlTokenizer;
import software.amazon.smithy.model.loader.IdlWhitespaceParser;
import software.amazon.smithy.model.loader.ModelSyntaxException;
import software.amazon.smithy.model.loader.StringTable;

/**
 * Captures tokens to a stack of token trees.
 */
final class CapturingTokenizer implements IdlTokenizer {

    private final IdlTokenizer delegate;
    private final TokenTree root = TokenTree.of(TreeType.IDL);
    private final Deque<TokenTree> trees = new ArrayDeque<>();
    private final IdlWhitespaceParser whitespaceParser;
    private final Function<CharSequence, String> stringTable = new StringTable(10); // 1024 entries
    private final List<CapturedToken> tokens = new ArrayList<>();
    private int cursor = 0;
    private TokenTree lastDocTree;

    CapturingTokenizer(IdlTokenizer delegate) {
        this.delegate = delegate;
        trees.add(root);
        this.whitespaceParser = new IdlWhitespaceParser(this);

        while (delegate.hasNext()) {
            delegate.next();
            tokens.add(CapturedToken.from(delegate));
        }
    }

    TokenTree getRoot() {
        return root;
    }

    @Override
    public String getSourceFilename() {
        return delegate.getSourceFilename();
    }

    @Override
    public CharSequence getModel() {
        return delegate.getModel();
    }

    private CapturedToken getToken() {
        return tokens.get(cursor);
    }

    @Override
    public int getPosition() {
        return getToken().getPosition() + getToken().getSpan();
    }

    @Override
    public int getLine() {
        return getToken().getEndLine();
    }

    @Override
    public int getColumn() {
        return getToken().getEndColumn();
    }

    @Override
    public IdlToken getCurrentToken() {
        return getToken().getToken();
    }

    @Override
    public int getCurrentTokenLine() {
        return getToken().getStartLine();
    }

    @Override
    public int getCurrentTokenColumn() {
        return getToken().getStartColumn();
    }

    @Override
    public int getCurrentTokenStart() {
        return getToken().getPosition();
    }

    @Override
    public int getCurrentTokenEnd() {
        return getPosition();
    }

    @Override
    public CharSequence getCurrentTokenStringSlice() {
        return getToken().getStringContents();
    }

    @Override
    public Number getCurrentTokenNumberValue() {
        return getToken().getNumberValue();
    }

    @Override
    public String getCurrentTokenError() {
        return getToken().getErrorMessage();
    }

    @Override
    public boolean hasNext() {
        return getToken().getToken() != IdlToken.EOF;
    }

    @Override
    public IdlToken next() {
        if (getToken().getToken() == IdlToken.EOF) {
            throw new NoSuchElementException();
        } else if (cursor > 0) {
            // Append the previous token when next is called.
            trees.getFirst().appendChild(CapturedToken.from(this));
        }
        return tokens.get(++cursor).getToken();
    }

    CapturedToken peek(int offsetFromPosition) {
        return cursor + offsetFromPosition >= tokens.size() - 1
               ? tokens.get(tokens.size() - 1)
               : tokens.get(cursor + offsetFromPosition);
    }

    CapturedToken peekPastSpaces(int offsetFromPosition) {
        return peekWhile(offsetFromPosition, token -> token == IdlToken.SPACE);
    }

    CapturedToken peekPastWs(int offsetFromPosition) {
        return peekWhile(offsetFromPosition, token -> token.isWhitespace() || token == IdlToken.DOC_COMMENT);
    }

    CapturedToken peekWhile(int offsetFromPosition, Predicate<IdlToken> predicate) {
        int position = cursor + offsetFromPosition;
        CapturedToken token = tokens.get(position);
        while (token.getToken() != IdlToken.EOF && predicate.test(token.getToken())) {
            token = tokens.get(++position);
        }
        return token;
    }

    TokenTree skipSpaces() {
        if (getCurrentToken() == IdlToken.SPACE) {
            return withState(TreeType.SPACES, whitespaceParser::skipSpaces);
        } else {
            return null;
        }
    }

    TokenTree skipWs() {
        IdlToken current = getCurrentToken();
        if (current.isWhitespace() || current == IdlToken.DOC_COMMENT) {
            return withState(TreeType.WHITESPACE, whitespaceParser::skipWsAndDocs);
        } else {
            return null;
        }
    }

    TokenTree expectAndSkipSpaces() {
        return withState(TreeType.SPACES, whitespaceParser::expectAndSkipSpaces);
    }

    TokenTree expectAndSkipWhitespace() {
        return withState(TreeType.WHITESPACE, () -> {
            whitespaceParser.expectAndSkipWhitespace();
            whitespaceParser.skipWsAndDocs();
        });
    }

    TokenTree expectAndSkipBr() {
        return withState(TreeType.BR, () -> {
            whitespaceParser.skipSpaces();
            whitespaceParser.expectAndSkipBr();
            // Skip docs too. Docs are later shifted out of this tree
            lastDocTree = getCurrentToken() == IdlToken.DOC_COMMENT ? skipWs() : null;
        });
    }

    String internString(CharSequence charSequence) {
        return stringTable.apply(charSequence);
    }

    TokenTree withState(TreeType state, Runnable parser) {
        return withState(state, this::defaultErrorRecovery, parser);
    }

    TokenTree withState(TreeType state, Runnable errorRecovery, Runnable parser) {
        return withState(state, errorRecovery, tree -> parser.run());
    }

    TokenTree withState(TreeType state, Consumer<TokenTree> parser) {
        return withState(state, this::defaultErrorRecovery, parser);
    }

    TokenTree withState(TreeType state, Runnable errorRecovery, Consumer<TokenTree> parser) {
        TokenTree tree = TokenTree.of(state);
        trees.getFirst().appendChild(tree);
        // Temporarily make this tree the current tree to capture tokens.
        trees.addFirst(tree);
        try {
            parser.accept(tree);
        } catch (ModelSyntaxException e) {
            TokenTree errorTree = new TokenTreeError(e.getMessageWithoutLocation());
            tree.appendChild(errorTree);
            if (getCurrentToken() != IdlToken.EOF) {
                // Temporarily make the error tree the current tree to capture error recovery tokens.
                trees.addFirst(errorTree);
                next();
                errorRecovery.run();
                trees.removeFirst();
            }
        } finally {
            trees.removeFirst();
        }
        return tree;
    }

    void skipShapeIdNamespace() {
        withState(TreeType.NAMESPACE, () -> {
            expect(IdlToken.IDENTIFIER);
            next();
            while (getCurrentToken() == IdlToken.DOT) {
                next();
                expect(IdlToken.IDENTIFIER);
                next();
            }
        });
    }

    void expectAndSkipAbsoluteShapeId(boolean allowMember) {
        withState(TreeType.SHAPE_ID, () -> {
            skipShapeIdNamespace();
            expect(IdlToken.POUND);
            next();
            skipRelativeRootShapeId(allowMember);
        });
    }

    void expectAndSkipShapeId(boolean allowMember) {
        IdlToken after = peekWhile(0, t -> t == IdlToken.DOT || t == IdlToken.IDENTIFIER).getToken();
        if (after == IdlToken.POUND) {
            expectAndSkipAbsoluteShapeId(true);
        } else {
            withState(TreeType.SHAPE_ID, () -> skipRelativeRootShapeId(true));
        }
    }

    void skipRelativeRootShapeId(boolean allowMember) {
        expect(IdlToken.IDENTIFIER);
        next();

        // Parse member if allowed and present.
        if (allowMember && getCurrentToken() == IdlToken.DOLLAR) {
            next(); // skip '$'
            withState(TreeType.SHAPE_ID_MEMBER, () -> {
                expect(IdlToken.IDENTIFIER);
                next();
            });
        }
    }

    // Look at the next to last tree, find a BR, and remove any trailing doc comments and place them at the
    // start of this tree.
    void movePreviousDocCommentsHere() {
        if (lastDocTree != null) {
            lastDocTree.getParent().removeChild(lastDocTree);
            trees.getFirst().appendChild(lastDocTree);
            lastDocTree = null;
        }
    }

    // Performs basic error recovery by skipping tokens until a $, identifier, or @ is found at column 1.
    private void defaultErrorRecovery() {
        lastDocTree = null;
        while (hasNext()) {
            if (getCurrentTokenColumn() == 1) {
                IdlToken token = getCurrentToken();
                if (token == IdlToken.DOLLAR || token == IdlToken.IDENTIFIER || token == IdlToken.AT
                        || token == IdlToken.RBRACE) {
                    return;
                }
            }
            next();
        }
    }
}
