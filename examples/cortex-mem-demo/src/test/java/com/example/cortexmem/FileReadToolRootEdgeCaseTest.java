package com.example.cortexmem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 第 295 轮复查：上轮已验证「根内可读 / 根外被拒」，但**没有验证「根本身是特殊路径」时
 * 解析器会怎样**。macOS 上 /tmp → /private/tmp、/var → /private/var 都是符号链接，
 * 而 {@code Path.toRealPath()} 会把它们解成真实路径——若比较时两侧口径不一致，
 * 合法路径会被**误判为越界**。
 *
 * <p>这三条是纯回归护栏，不是在断言新功能。
 */
class FileReadToolRootEdgeCaseTest {

    private static FileReadTool toolWithRoot(String root) {
        DemoProperties props = new DemoProperties();
        props.setFileReadRoot(root);
        return new FileReadTool(props);
    }

    @Test
    void worksWhenRootIsItselfASymlink() throws IOException {
        Path real = Files.createTempDirectory("real-root");
        Files.writeString(real.resolve("inside.txt"), "content via symlinked root");
        Path linkParent = Files.createTempDirectory("link-parent");
        Path link = linkParent.resolve("root-link");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | IOException e) {
            return; // 平台不支持符号链接
        }
        // 根配置成符号链接：解析器内部 toAbsolutePath().normalize() 不会解开它，
        // 而读取时 toRealPath() 会解开——两侧口径必须一致，否则合法文件被误拒。
        String out = toolWithRoot(link.toString()).readFile("inside.txt");
        assertEquals("content via symlinked root", out);
    }

    @Test
    void rootThatDoesNotExistReturnsNoContentAndDoesNotThrow() {
        // 第一版这里断言「应报越界错误」——**是断言写错了，不是代码有洞**。
        // 根不存在时 Files.exists(candidate) 为 false，toRealPath() 那段根本不会执行，
        // 于是走读取分支得到 NoSuchFileException。要验证的性质不是消息形状，而是：
        // ①调用方拿到的是字符串而不是异常（否则一次配置笔误就让 /demo/tool 返 500）；
        // ②响应里没有任何内容——根不存在，其下也不可能存在任何可读文件。
        String out = toolWithRoot("/definitely/not/here/at/all").readFile("anything.txt");
        assertTrue(out.startsWith("Error: "), out);
        assertTrue(!out.contains("PRIVATE"), out);
    }

    @Test
    void rootThatDoesNotExistStillRefusesPathsThatShareItsPrefix() {
        // 根不存在时唯一还能被构造出来的真实风险：/root 与 /root-elsewhere 共享字符串前缀。
        // Path.startsWith 按路径段比较，正确；按字符串比较则会把前者当成包含后者。
        String out = toolWithRoot("/definitely/not/here")
                .readFile("/definitely/not/here-elsewhere/secret.txt");
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
    }

    @Test
    void emptyRootFallsBackToWorkingDirectoryAndStillRefusesOutsideIt(@TempDir Path root) {
        // 空字符串 -> Path.of("") 是空路径，toAbsolutePath() 落到进程工作目录。
        // 此时根外的绝对路径仍须被拒。
        String out = toolWithRoot("").readFile("/etc/passwd");
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
    }

    @Test
    void trailingSeparatorInRootDoesNotBreakContainment() throws IOException {
        Path real = Files.createTempDirectory("trailing-root");
        Files.writeString(real.resolve("f.txt"), "ok");
        String out = toolWithRoot(real.toString() + "/").readFile("f.txt");
        assertEquals("ok", out);
    }

    @Test
    void siblingDirectoryWithSharedPrefixIsNotTreatedAsInside(@TempDir Path base) throws IOException {
        // 最容易被写错的边界：/root 与 /root-secrets 共享字符串前缀，但 /root-secrets
        // 不在 /root 之下。startsWith(Path) 按路径段比较，正确；按字符串比较则错。
        Path root = base.resolve("root");
        Path sibling = base.resolve("root-secrets");
        Files.createDirectories(root);
        Files.createDirectories(sibling);
        Files.writeString(sibling.resolve("secret.txt"), "TOP SECRET");
        Files.writeString(root.resolve("f.txt"), "ok");
        String out = toolWithRoot(root.toString()).readFile(sibling.resolve("secret.txt").toString());
        assertTrue(out.startsWith("Error: path is outside the allowed root"), out);
        assertTrue(!out.contains("TOP SECRET"));
    }

    @Test
    void dotSegmentsInsideRootStillResolve(@TempDir Path root) throws IOException {
        Files.createDirectories(root.resolve("a"));
        Files.writeString(root.resolve("a").resolve("f.txt"), "via dot segments");
        String out = toolWithRoot(root.toString()).readFile("./a/../a/f.txt");
        assertEquals("via dot segments", out);
    }
}
