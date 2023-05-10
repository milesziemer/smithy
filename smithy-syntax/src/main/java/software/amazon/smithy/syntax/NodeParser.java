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

// NodeValue =
//     NodeArray
//   / NodeObject
//   / Number
//   / NodeKeywords
//   / NodeStringValue
//
// NodeArray =
//     "[" [WS] *(NodeValue [WS]) "]"
//
// NodeObject =
//     "{" [WS] [NodeObjectKvp *(WS NodeObjectKvp)] [WS] "}"
//
// NodeObjectKvp =
//     NodeObjectKey [WS] ":" [WS] NodeValue
//
// NodeObjectKey =
//     QuotedText / Identifier
//
// Number =
//     [Minus] Int [Frac] [Exp]
//
// DecimalPoint =
//     %x2E ; .
//
// DigitOneToNine =
//     %x31-39 ; 1-9
//
// E =
//     %x65 / %x45 ; e E
//
// Exp =
//     E [Minus / Plus] 1*DIGIT
//
// Frac =
//     DecimalPoint 1*DIGIT
//
// Int =
//     Zero / (DigitOneToNine *DIGIT)
//
// Minus =
//     %x2D ; -
//
// Plus =
//     %x2B ; +
//
// Zero =
//     %x30 ; 0
//
// NodeKeywords =
//     %s"true" / %s"false" / %s"null"
//
// NodeStringValue =
//     ShapeId / TextBlock / QuotedText
//
// QuotedText =
//     DQUOTE *QuotedChar DQUOTE
//
// QuotedChar =
//     %x09        ; tab
//   / %x20-21     ; space - "!"
//   / %x23-5B     ; "#" - "["
//   / %x5D-10FFFF ; "]"+
//   / EscapedChar
//   / NL
//
// EscapedChar =
//     Escape (Escape / DQUOTE / %s"b" / %s"f"
//              / %s"n" / %s"r" / %s"t" / "/"
//              / UnicodeEscape)
//
// UnicodeEscape =
//     %s"u" Hex Hex Hex Hex
//
// Hex =
//     DIGIT / %x41-46 / %x61-66
//
// Escape =
//     %x5C ; backslash
//
// TextBlock =
//     ThreeDquotes [SP] NL *TextBlockContent ThreeDquotes
//
// TextBlockContent =
//     QuotedChar / (1*2DQUOTE 1*QuotedChar)
//
// ThreeDquotes =
//     DQUOTE DQUOTE DQUOTE
final class NodeParser implements Parser {

    private final CapturingTokenizer tokenizer;

    NodeParser(CapturingTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    @Override
    public TokenTree parse() {
        return tokenizer.withState(TreeType.NODE_VALUE, () -> {
            IdlToken token = tokenizer.expect(IdlToken.STRING, IdlToken.TEXT_BLOCK, IdlToken.NUMBER,
                                              IdlToken.IDENTIFIER, IdlToken.LBRACE, IdlToken.LBRACKET);
            switch (token) {
                case IDENTIFIER:
                    // Wrap identifier keywords in KEYWORD nodes.
                    parseIdentifier();
                    break;
                case STRING:
                case TEXT_BLOCK:
                case NUMBER:
                    tokenizer.next();
                    break;
                case LBRACE:
                    parseNodeObject();
                    break;
                case LBRACKET:
                default:
                    parseNodeArray();
                    break;
            }
        });
    }

    TokenTree parseNodeObjectKey() {
        return tokenizer.withState(TreeType.NODE_OBJECT_KEY, keyTree -> {
            tokenizer.expect(IdlToken.IDENTIFIER, IdlToken.STRING);
            tokenizer.next();
        });
    }

    void parseIdentifier() {
        tokenizer.expect(IdlToken.IDENTIFIER);
        if (tokenizer.isCurrentLexeme("true")
                || tokenizer.isCurrentLexeme("false")
                || tokenizer.isCurrentLexeme("null")) {
            tokenizer.withState(TreeType.KEYWORD, tokenizer::next);
        } else {
            tokenizer.next();
        }
    }

    void parseNodeArray() {
        tokenizer.withState(TreeType.NODE_ARRAY, () -> {
            tokenizer.expect(IdlToken.LBRACKET);
            tokenizer.next();
            tokenizer.skipWs();
            do {
                if (tokenizer.getCurrentToken() == IdlToken.RBRACKET) {
                    break;
                } else if (parse().getFirstChild(TreeType.ERROR) != null) {
                    // Stop trying to parse the array if an error occurred parsing the value.
                    return;
                } else {
                    tokenizer.skipWs();
                }
            } while (tokenizer.hasNext());
            tokenizer.expect(IdlToken.RBRACKET);
            tokenizer.next();
        });
    }

    void parseNodeObject() {
        tokenizer.withState(TreeType.NODE_OBJECT, () -> {
            tokenizer.expect(IdlToken.LBRACE);
            tokenizer.next();
            tokenizer.skipWs();

            while (tokenizer.hasNext()) {
                if (tokenizer.expect(IdlToken.RBRACE, IdlToken.STRING, IdlToken.IDENTIFIER) == IdlToken.RBRACE) {
                    break;
                }
                TokenTree kvp = tokenizer.withState(TreeType.NODE_OBJECT_KVP, () -> {
                    parseNodeObjectKey();
                    tokenizer.skipWs();
                    tokenizer.expect(IdlToken.COLON);
                    tokenizer.next();
                    tokenizer.skipWs();
                    parse();
                });
                if (kvp.getFirstChild(TreeType.ERROR) != null) {
                    // Stop trying to parse if an error occurred while parsing the kvp.
                    return;
                }
                tokenizer.skipWs();
            }

            tokenizer.expect(IdlToken.RBRACE);
            tokenizer.next();
        });
    }
}
