package org.jpos.ee.cli;

import org.apache.sshd.common.Factory;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.SessionAware;
import org.apache.sshd.server.session.ServerSession;
import org.jpos.q2.CLI;
import org.jpos.q2.Q2;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class CliShellFactory implements Factory<Command>
{
    Q2 q2;
    String[] prefixes;

    public CliShellFactory(Q2 q2, String[] prefixes)
    {
        this.q2 = q2;
        this.prefixes = prefixes;
    }

    public Command create()
    {
        return new JPosCLIShell();
    }

    public class JPosCLIShell implements Command, SessionAware
    {
        InputStream in;
        OutputStream out;
        OutputStream err;
        SshCLI cli = null;
        ServerSession serverSession;

        public void setInputStream(InputStream in)
        {
            this.in = in;
        }

        public void setOutputStream(OutputStream out)
        {
            this.out = out;
        }

        public void setErrorStream(OutputStream err)
        {
            this.err = err;
        }

        public void setExitCallback(ExitCallback exitCallback)
        {
        }

        public void setSession(ServerSession serverSession)
        {
            this.serverSession = serverSession;
        }

        public void start(Environment environment) throws IOException
        {
            MyFilterOutputStream fout = new MyFilterOutputStream(out);
            cli = new SshCLI(q2, in, fout, null, true);
            try
            {
                cli.setServerSession(serverSession);
                cli.start();
            }
            catch (Exception e)
            {
                throw new IOException("Could not start", e);
            }
        }

        public void destroy()
        {
            if (cli != null)
            {
                cli.stop();
            }
        }
    }

    protected class MyFilterOutputStream extends FilterOutputStream
    {
        public MyFilterOutputStream(OutputStream out)
        {
            super(out);
        }

        @Override
        public void write(int c) throws IOException
        {
            if (c == '\n' || c == '\r')
            {
                super.write('\r');
                super.write('\n');
                return;
            }
            super.write(c);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException
        {
            for (int i = off; i < len; i++)
            {
                write(b[i]);
            }
        }
    }

    public class SshCLI extends CLI
    {
        ServerSession serverSession = null;

        public SshCLI(Q2 q2, InputStream in, OutputStream out, String line, boolean keepRunning) throws IOException
        {
            super(q2, in, out, line, keepRunning);
        }

        protected boolean isRunning()
        {
            return !ctx.isStopped();
        }

        protected void markStopped()
        {
            ctx.setStopped(true);
        }

        protected void markStarted()
        {
            ctx.setStopped(false);
        }

        protected String[] getCompletionPrefixes()
        {
            return prefixes;
        }

        protected String getPrompt()
        {
            return "q2-ssh> ";
        }

        protected void handleExit()
        {
            if (serverSession != null)
            {
                try
                {
                    serverSession.disconnect(SshConstants.SSH2_DISCONNECT_BY_APPLICATION, "User triggered exit");
                }
                catch (IOException e)
                {
                }
            }
        }

        public void setServerSession(ServerSession serverSession)
        {
            this.serverSession = serverSession;
        }
    }
}
