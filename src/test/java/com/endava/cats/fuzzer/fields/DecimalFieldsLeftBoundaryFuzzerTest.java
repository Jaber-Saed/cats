package com.endava.cats.fuzzer.fields;

import com.endava.cats.io.ServiceCaller;
import com.endava.cats.report.TestCaseListener;
import com.endava.cats.util.CatsUtil;
import io.swagger.v3.oas.models.media.NumberSchema;
import org.apache.commons.lang3.math.NumberUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class DecimalFieldsLeftBoundaryFuzzerTest {

    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private DecimalFieldsLeftBoundaryFuzzer decimalFieldsLeftBoundaryFuzzer;

    @BeforeEach
    public void setup() {
        decimalFieldsLeftBoundaryFuzzer = new DecimalFieldsLeftBoundaryFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(decimalFieldsLeftBoundaryFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(NumberSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(decimalFieldsLeftBoundaryFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(decimalFieldsLeftBoundaryFuzzer.hasBoundaryDefined(nrSchema)).isTrue();
        Assertions.assertThat(decimalFieldsLeftBoundaryFuzzer.description()).isNotNull();
    }
}