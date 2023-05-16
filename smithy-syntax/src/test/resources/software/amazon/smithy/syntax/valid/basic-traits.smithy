$version: "2.0"

namespace com.test

@mixin
string SingleTrait

/// Foo
string DocCommentSyntaxSugar

@length(min: 1, max: 20)
string TraitWithMembers

@foo({ a: true })
string TraitWithObjectMember

/// Foo
@foo({ a: true })
@mixin
@length(min: 1, max: 20)
string MultipleTraits
