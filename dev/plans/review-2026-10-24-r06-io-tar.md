---
title: "r06 io-tar 审查发现"
type: guide
status: draft
phase: N/A
created: 2026-10-24
updated: 2026-10-24
summary: "runTar 会在失败前截断已有输出；多线程 fork 后调用 main 可能卡死，描述符快照和路径编码还有边界错误。"
---

# r06 io-tar 审查发现

读过 `io-tar/src/main/jni/jni.cpp` 与 `io-tar/src/main/kotlin/tiiehenry/android/compress/zstd/TarJNI.kt`。Kotlin 的两个 external 方法与 JNI 符号、参数一致。下面都出在 `runTar`。

## r06-1
- 严重度：高
- 症状：输出路径上已有文件时，调用最终失败，该文件仍被截成空文件，原内容回不来。
- 代码：`io-tar/src/main/jni/jni.cpp:44`、`io-tar/src/main/jni/jni.cpp:46`、`io-tar/src/main/jni/jni.cpp:60`、`io-tar/src/main/jni/jni.cpp:84`、`io-tar/src/main/jni/jni.cpp:97`
- 为什么是问题：标准输出在 `fork` 之前就以 `O_CREAT|O_TRUNC` 打开。标准错误路径打不开、`/proc/self/fd` 打不开，或 `fork` 失败时，函数关掉描述符并返回 -1。子进程在 `dup2` 之后打开输入失败会 `_exit(127)`，父进程经 `WEXITSTATUS` 把 127 交回调用方。这几条路径都还没写入归档，目标文件里的旧字节已经没了。标准错误文件同样在第 45 行被截断。
- 方向：子进程确认能写之后再换上新内容，失败返回时留下原文件。

## r06-2
- 严重度：中
- 症状：同进程其它线程正持有未做 fork 保护的锁时，这次调用停在 native 里，备份结束不了。
- 代码：`io-tar/src/main/jni/jni.cpp:21`、`io-tar/src/main/jni/jni.cpp:73`、`io-tar/src/main/jni/jni.cpp:92`、`io-tar/src/main/jni/jni.cpp:103`
- 为什么是问题：`fork` 只留当前线程，子进程接着调用 `main()`。`main` 会做分配、stdio 和其它 libc 调用，都不是异步信号安全函数。调用方是已经跑着 ART 和其它 so 的进程；没登记 `pthread_atfork` 的锁在子进程里会一直处于占用，`main` 不再返回，父进程停在 `waitpid`。第 92 行的 `_exit` 只包住 `main` 的正常返回，`main` 内部若直接 `exit`，仍会跑从父进程继承的 `atexit`。
- 方向：子进程只做异步信号安全的描述符整理，然后 exec 独立 tar。

## r06-3
- 严重度：中
- 症状：另一路操作在枚举描述符之后、fork 之前打开了管道写端时，这一路读端等不到 EOF，`waitpid` 不返回。
- 代码：`io-tar/src/main/jni/jni.cpp:58`、`io-tar/src/main/jni/jni.cpp:66`、`io-tar/src/main/jni/jni.cpp:73`、`io-tar/src/main/jni/jni.cpp:89`
- 为什么是问题：要关闭的描述符在 `fork` 前从 `/proc/self/fd` 抄进 `inherited`，子进程只关这份列表。抄完到 `fork` 之间其它线程新建的写端会被继承，且不在列表里。第 89–90 行写明：留下别的操作的管道写端，生产者结束后读端也等不到 EOF。`readdir` 这一趟本身也不是原子快照。借来的输入描述符已先 `dup2` 到标准输入，漏掉的是列表之外的写端。
- 方向：子进程接好标准流之后，从 3 到资源上限逐个 `close`。

## r06-4
- 严重度：低
- 症状：标准输出没打开时，归档写到已关闭的 fd 1，目标文件保持被截断后的空文件；fd 0 和 fd 1 都空着时，错误输出会写进归档文件。
- 代码：`io-tar/src/main/jni/jni.cpp:44`、`io-tar/src/main/jni/jni.cpp:77`、`io-tar/src/main/jni/jni.cpp:80`
- 为什么是问题：`open` 返回最小空闲描述符。fd 1 空闲时 `outFd` 就是 1，`dup2(1, 1)` 按 POSIX 是空操作，下一行无条件 `close(outFd)` 把标准输出关掉。输入侧只在 `childInput != STDIN_FILENO` 时才 `close`，输出没有同等判断。fd 0 和 fd 1 都空闲时，`errFd` 会落到 1，`dup2(outFd, 1)` 先关掉错误文件并让 1 变成输出文件，随后 `close(errFd)` 关掉标准输出，标准错误仍指着归档文件。
- 方向：`dup2` 成功后，仅当原描述符不是 0、1、2 时再关闭。

## r06-5
- 严重度：低
- 症状：路径或参数里带 U+10000 及以上字符时，打开的是另一串字节，原文件读不到，或写到另一个名字上。
- 代码：`io-tar/src/main/jni/jni.cpp:24`、`io-tar/src/main/jni/jni.cpp:38`、`io-tar/src/main/jni/jni.cpp:44`、`io-tar/src/main/jni/jni.cpp:83`
- 为什么是问题：`GetStringUTFChars` 返回 modified UTF-8。补充平面字符会编成 UTF-16 代理项的 CESU-8，`open` 和 tar 参数都按文件系统的 UTF-8 使用这些字节。U+0000 到 U+FFFF（含常用汉字）两种编码字节相同。成员路径走第 38 行的 `argv`，标准输出和输入路径走第 44、83 行。
- 方向：把 Java 字符串转成标准 UTF-8 再交给 `open` 和 `argv`。
