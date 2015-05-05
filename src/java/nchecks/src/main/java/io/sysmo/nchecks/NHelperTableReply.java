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

package io.sysmo.nchecks;

import io.sysmo.nchecks.NHelperReply;
import io.sysmo.nchecks.NHelperTableRow;
import io.sysmo.nchecks.NHelperTableItem;
import io.sysmo.nchecks.Const;

import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

import java.io.CharArrayWriter;
import javax.json.Json;
import javax.json.JsonWriter;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

/*
* This class build a simple message for client view. It will pop up
* with an icon corresponding to the status, and the message defined.
* It will fill the flag as defined in the xml check definition file
* to "value". See tutorials for more informations
*/

public class NHelperTableReply implements NHelperReply
{

    public static final int SUCCESS = 0;
    public static final int FAILURE = 1;
    private String  messageId   = "";
    private String  message     = "";
    private String  status      = Const.HELPER_SUCCESS;

    private ArrayList<NHelperTableRow> rows;
    public NHelperTableReply() {
        rows = new ArrayList<NHelperTableRow>();
    }

    /*
    *  Set the reply id.
    */
    public void setId(String val)  { messageId = val; }
    /*
    *  Set the status (Const.HELPER_SUCCESS | Const.HELPER_FAILURE)
    */
    public void setStatus(String val) { status = val; }
    /*
    *  Set the message string shown on top of the table.
    */
    public void setMessage(String val) { message = val; }

    /*
    *  Add a HelperTableRow to the table.
    */
    public void addRow(NHelperTableRow row) {
        rows.add(row);
    }

    public char[] toCharArray()
    {
        CharArrayWriter     buffer          = new CharArrayWriter();
        JsonBuilderFactory  factory         = Json.createBuilderFactory(null);
        JsonWriter          jsonWriter      = Json.createWriter(buffer);
        JsonObjectBuilder   objectbuilder   = factory.createObjectBuilder();
        JsonArrayBuilder    arraybuilder    = factory.createArrayBuilder();

        Iterator<NHelperTableRow> it = rows.iterator();

        while (it.hasNext()) {
            NHelperTableRow              row     = it.next();
            JsonObjectBuilder            rowObj  = factory.createObjectBuilder();
            List<NHelperTableItem>       items   = row.getItems();
            Iterator<NHelperTableItem>   tit     = items.iterator();
            while(tit.hasNext()) {
                NHelperTableItem item = tit.next();
                String key = item.getColumn();
                String value = item.getValue();
                rowObj.add(item.getColumn(), item.getValue());
            }
            arraybuilder.add(rowObj);
        }

        objectbuilder.add("status", status);
        objectbuilder.add("message", message);
        objectbuilder.add("id",   messageId);
        objectbuilder.add("rows", arraybuilder);
        jsonWriter.writeObject(objectbuilder.build());
        return buffer.toCharArray();
    }
}
