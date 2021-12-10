package com.endava.cats.command;

import com.endava.cats.args.ApiArguments;
import com.endava.cats.args.CheckArguments;
import com.endava.cats.args.ReportingArguments;
import com.endava.cats.model.factory.FuzzingDataFactory;
import com.endava.cats.report.ExecutionStatisticsListener;
import com.endava.cats.report.TestCaseListener;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import io.quarkus.test.junit.mockito.InjectSpy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import javax.inject.Inject;
import java.util.List;

@QuarkusTest
class CatsCommandTest {

    @Inject
    CatsCommand catsMain;
    @InjectSpy
    ExecutionStatisticsListener executionStatisticsListener;
    @Inject
    CheckArguments checkArguments;
    @Inject
    ReportingArguments reportingArguments;
    @Inject
    ApiArguments apiArguments;
    @InjectSpy
    FuzzingDataFactory fuzzingDataFactory;
    @InjectMock
    private TestCaseListener testCaseListener;

    @Test
    void shouldNotRunWhenNotRecognizedContentType() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore-nonjson.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");


        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(1)).endSession();
        Mockito.verify(spyMain, Mockito.times(0)).sortFuzzersByName();
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void shouldNotCallEndSessionWhenIOException() {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/not_existent.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");

        catsMain.run();
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(0)).endSession();
    }

    @Test
    void givenContractAndServerParameter_whenStartingCats_thenParametersAreProcessedSuccessfully() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/petstore.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(reportingArguments, "logData", List.of("org.apache.wire:debug", "com.endava.cats:warn"));

        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
        Mockito.verify(testCaseListener, Mockito.times(1)).startSession();
        Mockito.verify(testCaseListener, Mockito.times(1)).endSession();
        Mockito.verify(spyMain, Mockito.times(3)).sortFuzzersByName();

        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void givenAnOpenApiContract_whenStartingCats_thenTheContractIsCorrectlyParsed() throws Exception {
        ReflectionTestUtils.setField(apiArguments, "contract", "src/test/resources/openapi.yml");
        ReflectionTestUtils.setField(apiArguments, "server", "http://localhost:8080");
        ReflectionTestUtils.setField(checkArguments, "includeEmojis", true);
        ReflectionTestUtils.setField(checkArguments, "includeControlChars", true);
        ReflectionTestUtils.setField(checkArguments, "includeWhitespaces", true);
        CatsCommand spyMain = Mockito.spy(catsMain);
        spyMain.run();
        Mockito.verify(spyMain).createOpenAPI();
        Mockito.verify(spyMain).startFuzzing(Mockito.any(), Mockito.anyList());
        Mockito.verify(fuzzingDataFactory).fromPathItem(Mockito.eq("/pet"), Mockito.any(), Mockito.anyMap(), Mockito.any());
        Mockito.verify(fuzzingDataFactory, Mockito.times(0)).fromPathItem(Mockito.eq("/petss"), Mockito.any(), Mockito.anyMap(), Mockito.any());
        ReflectionTestUtils.setField(apiArguments, "contract", "empty");
        ReflectionTestUtils.setField(apiArguments, "server", "empty");
    }

    @Test
    void shouldReturnErrorsExitCode() {
        Mockito.when(executionStatisticsListener.getErrors()).thenReturn(190);

        Assertions.assertThat(catsMain.getExitCode()).isEqualTo(190);
    }
}