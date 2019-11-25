$version: "0.5.0"

use smithy.example1#Foo
use smithy.example1#Baz
use smithy.example1#Bar
use smithy.example1#trait1
use smithy.example1#trait2
use smithy.example1#trait3

namespace smithy.example2

@trait1
@trait2
@trait3
structure MyStruct {
    foo: Foo,
    baz: Baz,
    bar: Bar,
}
