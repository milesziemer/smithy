/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package software.amazon.smithy.mqtt.traits.validators;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.validation.AbstractValidator;
import software.amazon.smithy.model.validation.ValidationEvent;
import software.amazon.smithy.mqtt.traits.PublishTrait;
import software.amazon.smithy.utils.OptionalUtils;

/**
 * Publish operations must not contain event streams.
 */
public class MqttPublishInputValidator extends AbstractValidator {
    @Override
    public List<ValidationEvent> validate(Model model) {
        return model.shapes(OperationShape.class)
                .filter(shape -> shape.hasTrait(PublishTrait.class))
                .flatMap(shape -> validateOperation(model, shape))
                .collect(Collectors.toList());
    }

    private Stream<ValidationEvent> validateOperation(Model model, OperationShape operation) {
        return OptionalUtils.stream(operation.getInput().flatMap(model::getShape).flatMap(Shape::asStructureShape))
                .flatMap(input -> input.getAllMembers().values().stream()
                        .filter(member -> {
                            Shape target = model.expectShape(member.getTarget());
                            return target.isUnionShape() && target.hasTrait(StreamingTrait.class);
                        })
                        .map(member -> error(member, String.format(
                                "The input of `smithy.mqtt#publish` operations cannot contain event streams, "
                                + "and this member is used as part of the input of the `%s` operation.",
                                operation.getId()))));
    }
}
