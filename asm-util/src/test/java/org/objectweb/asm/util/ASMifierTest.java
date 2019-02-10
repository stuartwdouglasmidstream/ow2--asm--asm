// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package org.objectweb.asm.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.net.URLClassLoader;
import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.ClassLoaderIClassLoader;
import org.codehaus.janino.IClassLoader;
import org.codehaus.janino.Parser;
import org.codehaus.janino.Scanner;
import org.codehaus.janino.UnitCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.test.AsmTest;
import org.objectweb.asm.test.ClassFile;

/**
 * Unit tests for {@link ASMifier}.
 *
 * @author Eugene Kuleshov
 * @author Eric Bruneton
 */
// DontCheck(AbbreviationAsWordInName)
public class ASMifierTest extends AsmTest {

  private static final String EXPECTED_USAGE =
      "Prints the ASM code to generate the given class.\n"
          + "Usage: ASMifier [-debug] <fully qualified class name or class file name>\n";

  private static final IClassLoader ICLASS_LOADER =
      new ClassLoaderIClassLoader(new URLClassLoader(new URL[0]));

  @Test
  public void testConstructor() {
    assertDoesNotThrow(() -> new ASMifier());
    assertThrows(IllegalStateException.class, () -> new ASMifier() {});
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testDeprecatedVisitMethodInsn() {
    ASMifier asmifier = new ASMifier();

    asmifier.visitMethodInsn(Opcodes.INVOKESPECIAL, "owner", "name", "()V");

    assertEquals(
        "classWriter.visitMethodInsn(INVOKESPECIAL, \"owner\", \"name\", \"()V\", false);\n",
        asmifier.getText().get(0));
  }

  @Test
  @SuppressWarnings("deprecation")
  public void testDeprecatedVisitMethodInsn_asm4() {
    ASMifier asmifier = new ASMifier(Opcodes.ASM4, "classWriter", 0) {};

    asmifier.visitMethodInsn(Opcodes.INVOKESPECIAL, "owner", "name", "()V");

    assertEquals(
        "classWriter.visitMethodInsn(INVOKESPECIAL, \"owner\", \"name\", \"()V\", false);\n",
        asmifier.getText().get(0));
  }

  @Test
  public void testVisitMethodInsn_asm4() {
    ASMifier asmifier = new ASMifier(Opcodes.ASM4, "classWriter", 0) {};

    asmifier.visitMethodInsn(Opcodes.INVOKESPECIAL, "owner", "name", "()V", false);

    assertEquals(
        "classWriter.visitMethodInsn(INVOKESPECIAL, \"owner\", \"name\", \"()V\", false);\n",
        asmifier.getText().get(0));
  }

  /**
   * Tests that the code produced with an ASMifier compiles and generates the original class.
   *
   * @throws Exception if something goes wrong.
   */
  @ParameterizedTest
  @MethodSource(ALL_CLASSES_AND_LATEST_API)
  public void testAsmify_precompiledClass(
      final PrecompiledClass classParameter, final Api apiParameter) throws Exception {
    byte[] classFile = classParameter.getBytes();
    assumeTrue(classFile.length < Short.MAX_VALUE);
    StringWriter output = new StringWriter();
    TraceClassVisitor asmifier =
        new TraceClassVisitor(
            null,
            new ASMifier(apiParameter.value(), "classWriter", 0) {},
            new PrintWriter(output, true));

    new ClassReader(classFile)
        .accept(asmifier, new Attribute[] {new Comment(), new CodeComment()}, 0);

    // Janino can't compile JDK9 modules.
    assumeTrue(classParameter != PrecompiledClass.JDK9_MODULE);
    byte[] asmifiedClassFile = compile(classParameter.getName(), output.toString());
    Class<?> asmifiedClass = new ClassFile(asmifiedClassFile).newInstance().getClass();
    byte[] dumpClassFile = (byte[]) asmifiedClass.getMethod("dump").invoke(null);
    assertEquals(new ClassFile(classFile), new ClassFile(dumpClassFile));
  }

  private static byte[] compile(final String name, final String source)
      throws IOException, CompileException {
    Parser parser = new Parser(new Scanner(name, new StringReader(source)));
    UnitCompiler unitCompiler = new UnitCompiler(parser.parseCompilationUnit(), ICLASS_LOADER);
    return unitCompiler.compileUnit(true, true, true)[0].toByteArray();
  }

  @Test
  public void testMain_missingClassName() throws IOException {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = new String[0];

    ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertEquals("", output.toString());
    assertEquals(EXPECTED_USAGE, logger.toString());
  }

  @Test
  public void testMain_missingClassName_withDebug() throws IOException {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {"-debug"};

    ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertEquals("", output.toString());
    assertEquals(EXPECTED_USAGE, logger.toString());
  }

  @Test
  public void testMain_tooManyArguments() throws IOException {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {"-debug", getClass().getName(), "extraArgument"};

    ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertEquals("", output.toString());
    assertEquals(EXPECTED_USAGE, logger.toString());
  }

  @Test
  public void testMain_classFileNotFound() {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {"DoNotExist.class"};

    Executable main =
        () -> ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertThrows(IOException.class, main);
    assertEquals("", output.toString());
    assertEquals("", logger.toString());
  }

  @Test
  public void testMain_classNotFound() {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {"do\\not\\exist"};

    Executable main =
        () -> ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertThrows(IOException.class, main);
    assertEquals("", output.toString());
    assertEquals("", logger.toString());
  }

  @Test
  public void testMain_className() throws IOException {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {getClass().getName()};

    ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertTrue(output.toString().contains("public class ASMifierTestDump implements Opcodes"));
    assertTrue(output.toString().contains("\nmethodVisitor.visitLineNumber("));
    assertTrue(output.toString().contains("\nmethodVisitor.visitLocalVariable("));
    assertEquals("", logger.toString());
  }

  @Test
  public void testMain_className_withDebug() throws IOException {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {"-debug", getClass().getName()};

    ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertTrue(output.toString().contains("public class ASMifierTestDump implements Opcodes"));
    assertFalse(output.toString().contains("\nmethodVisitor.visitLineNumber("));
    assertFalse(output.toString().contains("\nmethodVisitor.visitLocalVariable("));
    assertEquals("", logger.toString());
  }

  @Test
  public void testMain_classFile() throws IOException {
    StringWriter output = new StringWriter();
    StringWriter logger = new StringWriter();
    String[] args = {
      ClassLoader.getSystemResource(getClass().getName().replace('.', '/') + ".class").getPath()
    };

    ASMifier.main(args, new PrintWriter(output, true), new PrintWriter(logger, true));

    assertTrue(output.toString().contains("public class ASMifierTestDump implements Opcodes"));
    assertEquals("", logger.toString());
  }
}
