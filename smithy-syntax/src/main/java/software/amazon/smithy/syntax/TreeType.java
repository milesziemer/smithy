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

public enum TreeType {
    IDL,

    CONTROL_SECTION,
    CONTROL_STATEMENT,

    METADATA_SECTION,
    METADATA_STATEMENT,

    SHAPE_SECTION,
    NAMESPACE_STATEMENT,

    USE_SECTION,
    USE_STATEMENT,

    SHAPE_STATEMENTS,
    SHAPE_OR_APPLY_STATEMENT,
    SHAPE_STATEMENT,
    SHAPE_BODY,

    SIMPLE_SHAPE_STATEMENT,
    ENUM_STATEMENT,

    LIST_STATEMENT,
    LIST_MEMBERS,
    LIST_MEMBER,
    ELIDED_LIST_MEMBER,
    EXPLICIT_LIST_MEMBER,

    MAP_STATEMENT,
    MAP_MEMBERS,
    MAP_KEY,
    ELIDED_MAP_KEY,
    EXPLICIT_MAP_KEY,
    MAP_VALUE,
    ELIDED_MAP_VALUE,
    EXPLICIT_MAP_VALUE,

    STRUCTURE_STATEMENT,
    STRUCTURE_MEMBER,
    EXPLICIT_STRUCTURE_MEMBER,
    ELIDED_STRUCTURE_MEMBER,

    UNION_STATEMENT,
    UNION_MEMBER,

    SERVICE_STATEMENT,
    OPERATION_STATEMENT,
    RESOURCE_STATEMENT,

    SHAPE_MIXINS,
    SHAPE_MEMBER,
    SHAPE_MEMBER_NAME,
    STRUCTURE_RESOURCE,
    VALUE_ASSIGNMENT,

    TRAIT_STATEMENTS,
    TRAIT,
    TRAIT_BODY,
    TRAIT_BODY_VALUE,
    TRAIT_STRUCTURE,
    TRAIT_STRUCTURE_KVP,

    APPLY_STATEMENT,
    APPLY_STATEMENT_SINGULAR,
    APPLY_STATEMENT_BLOCK,

    NODE_VALUE,
    NODE_ARRAY,
    NODE_OBJECT,
    NODE_OBJECT_KVP,
    NODE_OBJECT_KEY,

    SHAPE_ID,
    SHAPE_ID_MEMBER,
    NAMESPACE,

    SPACES,
    WHITESPACE,
    BR,

    KEYWORD,
    ERROR,
    TOKEN
}
