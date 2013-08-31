package org.nuprocess.unix;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;
import org.nuprocess.NuAbstractProcessHandler;
import org.nuprocess.NuProcess;
import org.nuprocess.NuProcessBuilder;
import org.nuprocess.NuProcessHandler;
import org.nuprocess.internal.BasePosixProcess;
import org.nuprocess.internal.LibC;

public class InterruptTest
{

    @Test
    public void testInterrupt1() throws InterruptedException
    {
        final Semaphore semaphore = new Semaphore(0);
        final AtomicInteger exitCode = new AtomicInteger();
        final AtomicInteger count = new AtomicInteger();

        NuProcessHandler processListener = new NuAbstractProcessHandler()
        {

            @Override
            public void onStart(NuProcess nuProcess)
            {
                nuProcess.wantWrite();
            }

            @Override
            public void onExit(int statusCode)
            {
                exitCode.set(statusCode);
                semaphore.release();
            }

            @Override
            public void onStdout(ByteBuffer buffer)
            {
                if (buffer == null)
                {
                    return;
                }

                count.addAndGet(buffer.remaining());
            }

            @Override
            public boolean onStdinReady(ByteBuffer buffer)
            {
                buffer.put("This is a test".getBytes());
                return true;
            }

        };

        NuProcessBuilder pb = new NuProcessBuilder(Arrays.asList("/bin/cat"), processListener);
        NuProcess process = pb.start();
        while (true)
        {
            if (count.get() > 10000)
            {
                process.destroy();
                break;
            }
            Thread.sleep(20);
        }

        semaphore.acquireUninterruptibly();
        Assert.assertEquals("Output did not matched expected result", 0, exitCode.get());
    }

    @Test
    public void chaosMonkey() throws InterruptedException
    {
        NuProcessHandler processListener = new NuAbstractProcessHandler()
        {

            @Override
            public void onStart(NuProcess nuProcess)
            {
                nuProcess.wantWrite();
            }

            @Override
            public void onStdout(ByteBuffer buffer)
            {
                if (buffer == null)
                {
                    return;
                }
            }

            @Override
            public boolean onStdinReady(ByteBuffer buffer)
            {
                buffer.put("This is a test".getBytes());
                return true;
            }

        };

        NuProcessBuilder pb = new NuProcessBuilder(Arrays.asList("/bin/cat"), processListener);
        List<NuProcess> processes = new LinkedList<>();
        for (int times = 0; times < 25; times++)
        {
            for (int i = 0; i < 50; i++)
            {
                processes.add(pb.start());
            }
    
            while (true)
            {
                Thread.sleep(20);
                int dead = (int) (Math.random() * processes.size());
                BasePosixProcess bpp = (BasePosixProcess) processes.remove(dead);
                LibC.kill(bpp.getPid(), LibC.SIGTERM);
    
                if (processes.isEmpty())
                {
                    break;
                }
            }
        }
    }
}