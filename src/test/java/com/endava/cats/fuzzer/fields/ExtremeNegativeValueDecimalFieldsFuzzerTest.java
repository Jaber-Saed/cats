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
public class ExtremeNegativeValueDecimalFieldsFuzzerTest {
    @Mock
    private ServiceCaller serviceCaller;

    @Mock
    private TestCaseListener testCaseListener;

    @Mock
    private CatsUtil catsUtil;

    private ExtremeNegativeValueDecimalFieldsFuzzer extremeNegativeValueDecimalFieldsFuzzer;

    @BeforeEach
    public void setup() {
        extremeNegativeValueDecimalFieldsFuzzer = new ExtremeNegativeValueDecimalFieldsFuzzer(serviceCaller, testCaseListener, catsUtil);
    }

    @Test
    public void givenANewExtremeNegativeValueDecimalFieldsFuzzer_whenCreatingANewInstance_thenTheMethodsBeingOverriddenAreMatchingTheDecimalFuzzer() {
        NumberSchema nrSchema = new NumberSchema();
        Assertions.assertThat(extremeNegativeValueDecimalFieldsFuzzer.getSchemasThatTheFuzzerWillApplyTo().stream().anyMatch(schema -> schema.isAssignableFrom(NumberSchema.class))).isTrue();
        Assertions.assertThat(NumberUtils.isCreatable(extremeNegativeValueDecimalFieldsFuzzer.getBoundaryValue(nrSchema))).isTrue();
        Assertions.assertThat(extremeNegativeValueDecimalFieldsFuzzer.hasBoundaryDefined(nrSchema)).isTrue();
        Assertions.assertThat(extremeNegativeValueDecimalFieldsFuzzer.description()).isNotNull();
        Assertions.assertThat(extremeNegativeValueDecimalFieldsFuzzer.typeOfDataSentToTheService()).isNotNull();
    }
}
