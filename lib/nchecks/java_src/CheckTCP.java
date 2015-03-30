/* Copyright (C) 2014, Sebastien Serre <sserre.bx@gmail.com>
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package io.sysmo.nchecks.modules;

import io.sysmo.nchecks.NChecksInterface;
import io.sysmo.nchecks.Argument;
import io.sysmo.nchecks.Reply;
import io.sysmo.nchecks.Const;

import java.util.Map;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.InetAddress;


public class CheckTCP implements NChecksInterface
{
    private String  host        = "";
    private int     port        = 0;
    private int     msWarning   = 500;
    private int     msCritical  = 2500;
    private int     msTimeout   = 5000;
    private String  refuseState = Const.STATUS_CRITICAL;
    private String  acceptState = Const.STATUS_OK;

    public CheckTCP()
    {
        System.out.println("init runnable");
    }

    public void setConfig(Map<String,Argument> config)
    {
        Argument hostArg        = config.get("host");
        Argument portArg        = config.get("port");
        Argument msWarningArg   = config.get("ms_warning");
        Argument msCriticalArg  = config.get("ms_critical");
        Argument msTimeoutArg   = config.get("ms_timeout");
        Argument refuseStateArg = config.get("refuse");
        Argument acceptStateArg = config.get("accept");

        if (hostArg         != null) { host = hostArg.getStr(); }
        if (portArg         != null) { port = portArg.getInt(); }
        if (msWarningArg    != null) { msWarning = msWarningArg.getInt(); }
        if (msCriticalArg   != null) { msCritical = msCriticalArg.getInt(); }
        if (msTimeoutArg    != null) { msTimeout = msTimeoutArg.getInt(); }
        if (refuseStateArg  != null) { refuseState = refuseStateArg.getStr(); }
        if (acceptStateArg  != null) { acceptState = acceptStateArg.getStr(); }

    }

    public Reply execute()
    {
        Reply reply = new Reply();

        if (port == 0 || port > 65535) {
            reply.setStatus(Const.STATUS_ERROR);
            reply.setReply("CheckTCP ERROR: Bad port definition " + port);
            return reply;
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByName(host);
        } catch (Exception e) {
            reply.setStatus(Const.STATUS_DOWN);
            reply.setReply("CheckTCP DOWN: Host lookup fail for: " + host);
            return reply;
        }


        Socket  sock = new Socket();
        Instant start;
        Instant stop;
        try {
            start = Instant.now();
            sock.connect(new InetSocketAddress(addr, port), msTimeout);
            stop = Instant.now();
            sock.close();
        } catch (Exception e) {
            reply.setReply(e.getMessage());
            reply.setStatus(refuseState);
            return reply;
        }

        long elapsed = ChronoUnit.MILLIS.between(start,stop);
        reply.putPerformance("ReplyDuration", elapsed);
        String st = null;
        if (Const.STATUS_OK.equals(acceptState))
        {
            if (elapsed >= msCritical) {
                st = Const.STATUS_CRITICAL;
            } else if (elapsed >= msWarning) {
                st = Const.STATUS_WARNING;
            } else {
                st = Const.STATUS_OK;
            }
        } else {
            st = acceptState;
        }
        reply.setStatus(st);
        reply.setReply("CheckTCP " + st + "Time elapsed: "  + elapsed + " milliseconds");
        return reply;
    }
}
