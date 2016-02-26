/**
 * Copyright 2014 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.arpnetworking.metrics.common.sources;

import com.arpnetworking.commons.observer.Observer;
import com.arpnetworking.metrics.common.parsers.Parser;
import com.arpnetworking.metrics.common.parsers.exceptions.ParsingException;
import com.arpnetworking.metrics.common.tailer.InitialPosition;
import com.arpnetworking.steno.LogBuilder;
import com.arpnetworking.steno.Logger;
import com.google.common.base.Charsets;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Matchers;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Tests for the FileSource class.
 *
 * @author Ville Koskela (vkoskela at groupon dot com)
 */
public class FileSourceTest {

    @SuppressWarnings("unchecked")
    @Before
    public void setUp() {
        _observer = Mockito.mock(Observer.class);
        _parser = Mockito.mock(Parser.class);
        _logger = Mockito.mock(Logger.class);
        _logBuilder = Mockito.mock(LogBuilder.class);
        Mockito.when(_logger.trace()).thenReturn(_logBuilder);
        Mockito.when(_logger.debug()).thenReturn(_logBuilder);
        Mockito.when(_logger.info()).thenReturn(_logBuilder);
        Mockito.when(_logger.warn()).thenReturn(_logBuilder);
        Mockito.when(_logger.error()).thenReturn(_logBuilder);
        Mockito.when(_logBuilder.setMessage(Matchers.anyString())).thenReturn(_logBuilder);
        Mockito.when(_logBuilder.addData(Matchers.anyString(), Matchers.anyString())).thenReturn(_logBuilder);
        Mockito.when(_logBuilder.addContext(Matchers.anyString(), Matchers.anyString())).thenReturn(_logBuilder);
        Mockito.when(_logBuilder.setEvent(Matchers.anyString())).thenReturn(_logBuilder);
        Mockito.when(_logBuilder.setThrowable(Matchers.any(Throwable.class))).thenReturn(_logBuilder);
    }

    @Test
    public void testParseData() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testParseData.log");
        final Path state = _directory.resolve("testParseData.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData = "Expected Data";
        Mockito.when(_parser.parse(expectedData.getBytes(Charsets.UTF_8))).thenReturn(expectedData);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(sleepInterval);

        Mockito.verify(_parser).parse(expectedData.getBytes(Charsets.UTF_8));
        Mockito.verify(_observer).notify(source, expectedData);
        source.stop();
    }

    @Test
    public void testTailFromEnd() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailFromEnd.log");
        Files.deleteIfExists(file);
        Files.createFile(file);

        final String expectedData = "Expected Data";
        final String unexpectedData = "Unexpected Data";
        Files.write(
                file,
                (unexpectedData + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);

        Mockito.when(_parser.parse(unexpectedData.getBytes(Charsets.UTF_8)))
               .thenThrow(new AssertionError("should not tail from beginning of file"));

        Mockito.when(_parser.parse(expectedData.getBytes(Charsets.UTF_8))).thenReturn(expectedData);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setInitialPosition(InitialPosition.END)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(sleepInterval);

        Mockito.verify(_parser, Mockito.never()).parse(unexpectedData.getBytes(Charsets.UTF_8));
        Mockito.verify(_parser).parse(expectedData.getBytes(Charsets.UTF_8));
        Mockito.verify(_observer).notify(source, expectedData);
        source.stop();
    }

    @Test
    public void testTailerFileNotFound() throws InterruptedException, IOException {
        final Path state = _directory.resolve("testTailerFileNotFound.log.state");
        Files.deleteIfExists(state);
        final Path file = _directory.resolve("testTailerFileNotFound.log");
        Files.deleteIfExists(file);
        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(INTERVAL)),
                _logger);

        source.start();
        Thread.sleep(SLEEP_INTERVAL);
        Mockito.verify(_logger).warn();
        Mockito.verify(_logBuilder, Mockito.atLeastOnce()).setMessage("Tailer file not found");
        source.stop();
    }

    @Test
    public void testTailerFileNotFoundInterval() throws InterruptedException, IOException {
        final Path state = _directory.resolve("testTailerFileNotFoundInterval.log.state");
        Files.deleteIfExists(state);
        final Path file = _directory.resolve("testTailerFileNotFoundInterval.log");
        Files.deleteIfExists(file);
        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(INTERVAL)),
                _logger);

        source.start();
        Thread.sleep(SLEEP_INTERVAL);
        Mockito.verify(_logger).warn();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file not found"));
        Thread.sleep(SLEEP_INTERVAL * 2);
        Mockito.verify(_logger).warn();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file not found"));
        source.stop();
    }

    @Test
    public void testTailerLogRotationRename() throws IOException, InterruptedException {
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationRename.log");
        final Path state = _directory.resolve("testTailerLogRotationRename.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        Files.write(file, "Existing data in the log file\n".getBytes(Charsets.UTF_8));

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(INTERVAL)),
                _logger);

        source.start();
        Thread.sleep(SLEEP_INTERVAL);
        renameRotate(file);
        Files.createFile(file);
        Thread.sleep(2 * SLEEP_INTERVAL);

        Mockito.verify(_logger).debug();
        Mockito.verify(_logBuilder).setMessage("Tailer file rotate");
        source.stop();
    }

    // TODO(vkoskela): Rotation from empty file to empty file not supported [MAI-189]
    @Ignore
    @Test
    public void testTailerLogRotationRenameFromEmpty() throws IOException, InterruptedException {
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationRenameFromEmpty.log");
        final Path state = _directory.resolve("testTailerLogRotationRenameFromEmpty.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(INTERVAL)),
                _logger);

        source.start();
        Thread.sleep(SLEEP_INTERVAL);
        renameRotate(file);
        Files.createFile(file);
        Thread.sleep(2 * SLEEP_INTERVAL);

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    @Test
    public void testTailerLogRotationCopyTruncate() throws IOException, InterruptedException {
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationCopyTruncate.log");
        final Path state = _directory.resolve("testTailerLogRotationCopyTruncate.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        Files.write(file, "Existing data in the log file\n".getBytes(Charsets.UTF_8));

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(INTERVAL)),
                _logger);

        source.start();
        Thread.sleep(SLEEP_INTERVAL);
        copyRotate(file);
        truncate(file);
        Thread.sleep(2 * SLEEP_INTERVAL);

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    // TODO(vkoskela): Rotation from empty file to empty file not supported [MAI-189]
    // TODO(vkoskela): Copy truncate not supported [MAI-188]
    @Ignore
    @Test
    public void testTailerLogRotationCopyTruncateFromEmpty() throws IOException, InterruptedException {
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationCopyTruncate.log");
        final Path state = _directory.resolve("testTailerLogRotationCopyTruncate.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(INTERVAL)),
                _logger);

        source.start();
        Thread.sleep(SLEEP_INTERVAL);
        copyRotate(file);
        truncate(file);
        Thread.sleep(2 * SLEEP_INTERVAL);

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    @Test
    public void testTailerLogRotationRenameWithData() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationRenameWithData.log");
        final Path state = _directory.resolve("testTailerLogRotationRenameWithData.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData = "Expected Data";
        Mockito.when(_parser.parse(expectedData.getBytes(Charsets.UTF_8))).thenReturn(expectedData);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        renameRotate(file);
        Files.createFile(file);
        Mockito.verifyZeroInteractions(_parser);
        Mockito.verifyZeroInteractions(_observer);
        Thread.sleep(3 * sleepInterval);

        Mockito.verify(_parser).parse(expectedData.getBytes(Charsets.UTF_8));
        Mockito.verify(_observer).notify(source, expectedData);
        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder, Mockito.after(10000)).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    // TODO(vkoskela): Copy truncate not supported [MAI-188]
    // In this case the file is copied and truncated before the tailer is able
    // to read the data.  Since the tailer does not understand where the file
    // is copied to it has no chance to read it.
    @Ignore
    @Test
    public void testTailerLogRotationCopyTruncateWithData() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationCopyTruncateWithData.log");
        final Path state = _directory.resolve("testTailerLogRotationCopyTruncateWithData.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData = "Expected Data";
        Mockito.when(_parser.parse(expectedData.getBytes(Charsets.UTF_8))).thenReturn(expectedData);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        copyRotate(file);
        truncate(file);
        Mockito.verifyZeroInteractions(_parser);
        Mockito.verifyZeroInteractions(_observer);
        Thread.sleep(3 * sleepInterval);

        Mockito.verify(_parser).parse(expectedData.getBytes(Charsets.UTF_8));
        Mockito.verify(_observer).notify(source, expectedData);
        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    @Test
    public void testTailerLogRotationRenameWithDataToOldAndNew() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationRenameWithDataToOldAndNew.log");
        final Path state = _directory.resolve("testTailerLogRotationRenameWithDataToOldAndNew.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData1 = "Expected Data 1 must be larger";
        final String expectedData2 = "Expected Data 2";
        Mockito.when(_parser.parse(expectedData1.getBytes(Charsets.UTF_8))).thenReturn(expectedData1);
        Mockito.when(_parser.parse(expectedData2.getBytes(Charsets.UTF_8))).thenReturn(expectedData2);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData1 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        renameRotate(file);
        Files.createFile(file);
        Files.write(
                file,
                (expectedData2 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Mockito.verifyZeroInteractions(_parser);
        Mockito.verifyZeroInteractions(_observer);
        Thread.sleep(3 * sleepInterval);

        final ArgumentCaptor<byte[]> parserCapture = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<Object> notifyCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.verify(_parser, Mockito.times(2)).parse(parserCapture.capture());
        final List<byte[]> parserValues = parserCapture.getAllValues();
        // CHECKSTYLE.OFF: IllegalInstantiation - This is ok for String from byte[]
        Assert.assertTrue("actual=" + new String(parserValues.get(0), Charsets.UTF_8),
                Arrays.equals(expectedData1.getBytes(Charsets.UTF_8), parserValues.get(0)));
        Assert.assertTrue("actual=" + new String(parserValues.get(1), Charsets.UTF_8),
                Arrays.equals(expectedData2.getBytes(Charsets.UTF_8), parserValues.get(1)));
        // CHECKSTYLE.ON: IllegalInstantiation

        Mockito.verify(_observer, Mockito.times(2)).notify(Matchers.eq(source), notifyCapture.capture());
        final List<Object> notifyValues = notifyCapture.getAllValues();
        Assert.assertEquals(expectedData1, notifyValues.get(0));
        Assert.assertEquals(expectedData2, notifyValues.get(1));

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    // TODO(vkoskela): Copy truncate not supported [MAI-188]
    // The tailer has no opportunity to see the data block written immediately
    // before the copy-truncate. This is probably the most difficult case to
    // fix for copy-truncate. Unfortunately, either the tailer needs knowledge
    // of the file rotation scheme (to look for the copied file) or may be able
    // to discover this file with a file system watcher.
    @Ignore
    @Test
    public void testTailerLogRotationCopyTruncateWithDataToOldAndNew() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationCopyTruncateWithDataToOldAndNew.log");
        final Path state = _directory.resolve("testTailerLogRotationCopyTruncateWithDataToOldAndNew.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData1 = "Expected Data 1 must be larger";
        final String expectedData2 = "Expected Data 2";
        Mockito.when(_parser.parse(expectedData1.getBytes(Charsets.UTF_8))).thenReturn(expectedData1);
        Mockito.when(_parser.parse(expectedData2.getBytes(Charsets.UTF_8))).thenReturn(expectedData2);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData1 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        copyRotate(file);
        truncate(file);
        Files.write(
                file,
                (expectedData2 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Mockito.verifyZeroInteractions(_parser);
        Mockito.verifyZeroInteractions(_observer);
        Thread.sleep(3 * sleepInterval);

        final ArgumentCaptor<byte[]> parserCapture = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<Object> notifyCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.verify(_parser, Mockito.times(2)).parse(parserCapture.capture());
        final List<byte[]> parserValues = parserCapture.getAllValues();
        // CHECKSTYLE.OFF: IllegalInstantiation - This is ok for String from byte[]
        Assert.assertTrue("actual=" + new String(parserValues.get(0), Charsets.UTF_8),
                Arrays.equals(expectedData1.getBytes(Charsets.UTF_8), parserValues.get(0)));
        Assert.assertTrue("actual=" + new String(parserValues.get(1), Charsets.UTF_8),
                Arrays.equals(expectedData2.getBytes(Charsets.UTF_8), parserValues.get(1)));
        // CHECKSTYLE.ON: IllegalInstantiation

        Mockito.verify(_observer, Mockito.times(2)).notify(source, notifyCapture.capture());
        final List<Object> notifyValues = notifyCapture.getAllValues();
        Assert.assertEquals(expectedData1, notifyValues.get(0));
        Assert.assertEquals(expectedData2, notifyValues.get(1));

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTailerLogRotationRenameDroppedData() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationRenameDroppedData.log");
        final Path state = _directory.resolve("testTailerLogRotationRenameDroppedData.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData1 = "Expected Data 1 must be larger";
        final String expectedData2 = "Expected Data 2 plus";
        final String expectedData3 = "Expected Data 3";
        Mockito.when(_parser.parse(expectedData1.getBytes(Charsets.UTF_8))).thenReturn(expectedData1);
        Mockito.when(_parser.parse(expectedData2.getBytes(Charsets.UTF_8))).thenReturn(expectedData2);
        Mockito.when(_parser.parse(expectedData3.getBytes(Charsets.UTF_8))).thenReturn(expectedData3);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Files.write(
                file,
                (expectedData1 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData2 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        renameRotate(file);
        Files.createFile(file);
        Files.write(
                file,
                (expectedData3 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(3 * sleepInterval);

        final ArgumentCaptor<byte[]> parserCapture = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<Object> notifyCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.verify(_parser, Mockito.times(3)).parse(parserCapture.capture());
        final List<byte[]> parserValues = parserCapture.getAllValues();
        // CHECKSTYLE.OFF: IllegalInstantiation - This is ok for String from byte[]
        Assert.assertTrue("actual=" + new String(parserValues.get(0), Charsets.UTF_8),
                Arrays.equals(expectedData1.getBytes(Charsets.UTF_8), parserValues.get(0)));
        Assert.assertTrue("actual=" + new String(parserValues.get(1), Charsets.UTF_8),
                Arrays.equals(expectedData2.getBytes(Charsets.UTF_8), parserValues.get(1)));
        Assert.assertTrue("actual=" + new String(parserValues.get(2), Charsets.UTF_8),
                Arrays.equals(expectedData3.getBytes(Charsets.UTF_8), parserValues.get(2)));
        // CHECKSTYLE.ON: IllegalInstantiation

        Mockito.verify(_observer, Mockito.times(3)).notify(Matchers.eq(source), notifyCapture.capture());
        final List<Object> notifyValues = notifyCapture.getAllValues();
        Assert.assertEquals(expectedData1, notifyValues.get(0));
        Assert.assertEquals(expectedData2, notifyValues.get(1));
        Assert.assertEquals(expectedData3, notifyValues.get(2));

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    // TODO(vkoskela): Copy truncate not supported [MAI-188]
    // The tailer has no opportunity to see the data block written immediately
    // before the copy-truncate. This is probably the most difficult case to
    // fix for copy-truncate. Unfortunately, either the tailer needs knowledge
    // of the file rotation scheme (to look for the copied file) or may be able
    // to discover this file with a file system watcher.
    @Ignore
    @SuppressWarnings("unchecked")
    @Test
    public void testTailerLogCopyTruncateRenameDroppedData() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogCopyTruncateRenameDroppedData.log");
        final Path state = _directory.resolve("testTailerLogCopyTruncateRenameDroppedData.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData1 = "Expected Data 1 must be larger";
        final String expectedData2 = "Expected Data 2 plus";
        final String expectedData3 = "Expected Data 3";
        Mockito.when(_parser.parse(expectedData1.getBytes(Charsets.UTF_8))).thenReturn(expectedData1);
        Mockito.when(_parser.parse(expectedData2.getBytes(Charsets.UTF_8))).thenReturn(expectedData2);
        Mockito.when(_parser.parse(expectedData3.getBytes(Charsets.UTF_8))).thenReturn(expectedData3);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Files.write(
                file,
                (expectedData1 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(sleepInterval);
        Files.write(
                file,
                (expectedData2 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        copyRotate(file);
        truncate(file);
        Files.write(
                file,
                (expectedData3 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(3 * sleepInterval);

        final ArgumentCaptor<byte[]> parserCapture = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<Object> notifyCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.verify(_parser, Mockito.times(3)).parse(parserCapture.capture());
        final List<byte[]> parserValues = parserCapture.getAllValues();
        // CHECKSTYLE.OFF: IllegalInstantiation - This is ok for String from byte[]
        Assert.assertTrue("actual=" + new String(parserValues.get(0), Charsets.UTF_8),
                Arrays.equals(expectedData1.getBytes(Charsets.UTF_8), parserValues.get(0)));
        Assert.assertTrue("actual=" + new String(parserValues.get(1), Charsets.UTF_8),
                Arrays.equals(expectedData2.getBytes(Charsets.UTF_8), parserValues.get(1)));
        Assert.assertTrue("actual=" + new String(parserValues.get(2), Charsets.UTF_8),
                Arrays.equals(expectedData3.getBytes(Charsets.UTF_8), parserValues.get(2)));
        // CHECKSTYLE.ON: IllegalInstantiation

        Mockito.verify(_observer, Mockito.times(3)).notify(Matchers.eq(source), notifyCapture.capture());
        final List<Object> notifyValues = notifyCapture.getAllValues();
        Assert.assertEquals(expectedData1, notifyValues.get(0));
        Assert.assertEquals(expectedData2, notifyValues.get(1));
        Assert.assertEquals(expectedData3, notifyValues.get(2));

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testTailerLogRotationRenameSmallToLarge() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationRenameSmallToLarge.log");
        final Path state = _directory.resolve("testTailerLogRotationRenameSmallToLarge.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData1 = "Expected Data 1 small";
        final String expectedData2 = "Expected Data 2 must be larger";
        Mockito.when(_parser.parse(expectedData1.getBytes(Charsets.UTF_8))).thenReturn(expectedData1);
        Mockito.when(_parser.parse(expectedData2.getBytes(Charsets.UTF_8))).thenReturn(expectedData2);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Files.write(
                file,
                (expectedData1 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(sleepInterval);
        renameRotate(file);
        Files.createFile(file);
        Files.write(
                file,
                (expectedData2 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(3 * sleepInterval);

        final ArgumentCaptor<byte[]> parserCapture = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<Object> notifyCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.verify(_parser, Mockito.times(2)).parse(parserCapture.capture());
        final List<byte[]> parserValues = parserCapture.getAllValues();
        // CHECKSTYLE.OFF: IllegalInstantiation - This is ok for String from byte[]
        Assert.assertTrue("actual=" + new String(parserCapture.getValue(), Charsets.UTF_8),
                Arrays.equals(expectedData1.getBytes(Charsets.UTF_8), parserValues.get(0)));
        Assert.assertTrue("actual=" + new String(parserCapture.getValue(), Charsets.UTF_8),
                Arrays.equals(expectedData2.getBytes(Charsets.UTF_8), parserValues.get(1)));
        // CHECKSTYLE.ON: IllegalInstantiation

        Mockito.verify(_observer, Mockito.times(2)).notify(Matchers.eq(source), notifyCapture.capture());
        final List<Object> notifyValues = notifyCapture.getAllValues();
        Assert.assertEquals(expectedData1, notifyValues.get(0));
        Assert.assertEquals(expectedData2, notifyValues.get(1));

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    // TODO(vkoskela): Copy truncate not supported [MAI-188]
    // The small data block is read but the larger block which replaces it
    // immediately after the copy-truncate operation appears to the tailer
    // to just be more data. There is a relatively simple fix to this problem,
    // add a check if the character just before the read position is not a new
    // line character then the file was rotated. This will not cover all cases
    // but should cover a large majority. Beyond this fix the only ways to
    // detect the copy truncate are hash prefix comparison or inode comparison
    // before every read.
    @Ignore
    @SuppressWarnings("unchecked")
    @Test
    public void testTailerLogRotationCopyTruncateSmallToLarge() throws IOException, InterruptedException, ParsingException {
        final long interval = 500;
        final long sleepInterval = 600;
        Files.createDirectories(_directory);
        final Path file = _directory.resolve("testTailerLogRotationCopyTruncateSmallToLarge.log");
        final Path state = _directory.resolve("testTailerLogRotationCopyTruncateSmallToLarge.log.state");
        Files.deleteIfExists(file);
        Files.createFile(file);
        Files.deleteIfExists(state);

        final String expectedData1 = "Expected Data 1 small";
        final String expectedData2 = "Expected Data 2 must be larger";
        Mockito.when(_parser.parse(expectedData1.getBytes(Charsets.UTF_8))).thenReturn(expectedData1);
        Mockito.when(_parser.parse(expectedData2.getBytes(Charsets.UTF_8))).thenReturn(expectedData2);

        final FileSource<Object> source = new FileSource<>(
                new FileSource.Builder<>()
                        .setSourceFile(file)
                        .setStateFile(state)
                        .setParser(_parser)
                        .setInterval(Duration.millis(interval)),
                _logger);

        source.attach(_observer);
        source.start();

        Files.write(
                file,
                (expectedData1 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(sleepInterval);
        copyRotate(file);
        truncate(file);
        Files.write(
                file,
                (expectedData2 + "\n").getBytes(Charsets.UTF_8),
                StandardOpenOption.APPEND, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
        Thread.sleep(3 * sleepInterval);

        final ArgumentCaptor<byte[]> parserCapture = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<Object> notifyCapture = ArgumentCaptor.forClass(Object.class);

        Mockito.verify(_parser, Mockito.times(2)).parse(parserCapture.capture());
        final List<byte[]> parserValues = parserCapture.getAllValues();
        // CHECKSTYLE.OFF: IllegalInstantiation - This is ok for String from byte[]
        Assert.assertTrue("actual=" + new String(parserValues.get(0), Charsets.UTF_8),
                Arrays.equals(expectedData1.getBytes(Charsets.UTF_8), parserValues.get(0)));
        Assert.assertTrue("actual=" + new String(parserValues.get(1), Charsets.UTF_8),
                Arrays.equals(expectedData2.getBytes(Charsets.UTF_8), parserValues.get(1)));
        // CHECKSTYLE.ON: IllegalInstantiation

        Mockito.verify(_observer, Mockito.times(2)).notify(Matchers.eq(source), notifyCapture.capture());
        final List<Object> notifyValues = notifyCapture.getAllValues();
        Assert.assertEquals(expectedData1, notifyValues.get(0));
        Assert.assertEquals(expectedData2, notifyValues.get(1));

        Mockito.verify(_logger).info();
        Mockito.verify(_logBuilder).setMessage(Mockito.contains("Tailer file rotate"));
        source.stop();
    }

    // The file.getFileName will not return null because of the check above.
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private void renameRotate(final Path file) throws IOException {
        if (file.getNameCount() == 0) {
            throw new IllegalArgumentException("No name elements in " + file);
        }
        final Path destination = file.resolveSibling(
                file.getFileName().toString().replaceAll("\\.log", "")
                        + "."
                        + _dateFormat.format(new Date())
                        + ".log");
        Files.deleteIfExists(destination);
        Files.move(
                file,
                destination);
    }

    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    private void copyRotate(final Path file) throws IOException {
        // The file.getFileName will not return null because of the check above.
        if (file.getNameCount() == 0) {
            throw new IllegalArgumentException("No name elements in " + file);
        }
        final Path destination = file.resolveSibling(
                file.getFileName().toString().replaceAll("\\.log", "")
                        + "."
                        + _dateFormat.format(new Date())
                        + ".log");
        Files.deleteIfExists(destination);
        Files.copy(
                file,
                destination);
    }

    private void truncate(final Path file) {
        try {
            FileChannel.open(file, StandardOpenOption.WRITE).truncate(0L).close();
        } catch (final IOException e) {
            // Ignore
        }
    }

    private Observer _observer;
    private Logger _logger;
    private LogBuilder _logBuilder;
    private Parser<Object> _parser;
    private final SimpleDateFormat _dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH");
    private final Path _directory = Paths.get("./target/tmp/filter/FileSourceTest");

    private static final long INTERVAL = 50;
    private static final long SLEEP_INTERVAL = INTERVAL + 25;
}