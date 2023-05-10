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

import software.amazon.smithy.model.loader.IdlToken;

// UseSection =
//     *(UseStatement)
//
// UseStatement =
//     %s"use" SP AbsoluteRootShapeId BR
final class UseSectionParser implements Parser {

    private final CapturingTokenizer tokenizer;

    UseSectionParser(CapturingTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public TokenTree parse() {
        return tokenizer.withState(TreeType.USE_SECTION, () -> {
            while (tokenizer.getCurrentToken() == IdlToken.IDENTIFIER) {
                // Don't over-parse here for unions.
                String keyword = tokenizer.internString(tokenizer.getCurrentTokenLexeme());
                if (!keyword.equals("use")) {
                    break;
                }
                tokenizer.withState(TreeType.USE_STATEMENT, () -> {
                    tokenizer.withState(TreeType.KEYWORD, tokenizer::next);
                    tokenizer.expectAndSkipSpaces();
                    tokenizer.expectAndSkipAbsoluteShapeId(false);
                    tokenizer.expectAndSkipBr();
                });
            }
        });
    }
}
