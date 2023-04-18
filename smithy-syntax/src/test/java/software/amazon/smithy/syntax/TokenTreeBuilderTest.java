package software.amazon.smithy.syntax;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.loader.IdlToken;
import software.amazon.smithy.model.loader.IdlTokenizer;

public class TokenTreeBuilderTest {
    @Test
    public void createsTreeFromParser() {
        String model = "$version: \"2.0\"\n\n"
                       + "$foo: b.ar\n"
                       + "/// Foo\n"
                       + "metadata foo = \"bar\"\n"
                       + "namespace hello.there\n\n"
                       + "use smithy.example#Abc\n"
                       + "use smithy.other#Bar\n\n"
                       + "string Foo\n"
                       + "integer Bar\n";
        IdlTokenizer tokenizer = IdlTokenizer.builder()
                .model(model)
                .filename("example.smithy")
                .build();
        TokenTreeBuilder builder = new TokenTreeBuilder(tokenizer);
        TokenTree tree = builder.create();
        TokenTree match = tree.findAt(3, 4);

        System.out.println(tree);

        assertThat(match.getTokens().get(0).getLexeme().toString(), equalTo("foo"));
        assertThat(match.getTokens().get(0).getToken(), is(IdlToken.IDENTIFIER));
    }

    @Test
    public void rearrangesTreesWhenDocCommentsFound() {
        String model = "$version: \"2.0\"\n\n"
                       + "namespace hello.there\n\n"
                       + "/// Foo\n"
                       + "string Foo\n"
                       //+ "/// Hi\n"
                       + "string Baz\n";
        IdlTokenizer tokenizer = IdlTokenizer.builder()
                .model(model)
                .filename("example.smithy")
                .build();
        TokenTreeBuilder builder = new TokenTreeBuilder(tokenizer);
        TokenTree tree = builder.create();

        System.out.println(tree);
    }
}
