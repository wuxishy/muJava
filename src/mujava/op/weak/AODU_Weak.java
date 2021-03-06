/**
 * Copyright (C) 2015  the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package mujava.op.weak;

import openjava.mop.*;
import openjava.ptree.*;
import java.io.*;

/**
 * <p>Generate AODU (Arithmetic Operator Deletion (Unary)) mutants --
 *    delete a unary operator (arithmetic -) before each variable or 
 *    expression
 * </p> 
 * @author Haoyuan Sun
 * @version 0.1a
 *
 */

public class AODU_Weak extends Arithmetic_OP_Weak {
    public AODU_Weak(FileEnvironment file_env, ClassDeclaration cdecl, CompilationUnit comp_unit) {
        super(file_env, comp_unit);
    }

    public void visit(UnaryExpression p) throws ParseTreeException {
        if (mutExpression == null) mutExpression = p;

        int op = p.getOperator();
        if (op == UnaryExpression.MINUS) {
            // original -- with minus
            typeStack.add(getType(p));
            exprStack.add(new UnaryExpression(genVar(counter+2), UnaryExpression.MINUS)); // +0
            // mutant -- without minus
            typeStack.add(getType(p));
            exprStack.add(genVar(counter+2)); // +1
            // expression
            typeStack.add(getType(p));
            exprStack.add(p.getExpression()); // +2
            counter += 3;

            outputToFile();

            pop(3);
        }

        if(mutExpression.getObjectID() == p.getObjectID()) mutExpression = null;
    }

    /**
     * Output COD mutants to files
     */
    public void outputToFile() {
        if (comp_unit == null)
            return;

        String f_name;
        num++;
        f_name = getSourceName("AODU");
        String mutant_dir = getMuantID("AODU");

        try {
            PrintWriter out = getPrintWriter(f_name);
            //PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));
            InstrumentationCodeWriter writer = new InstrumentationCodeWriter(mutant_dir, out);

            writer.setEnclose(encBlock);
            writer.setBlock(mutBlock);
            writer.setStatement(mutStatement);
            writer.setExpression(mutExpression);
            writer.setInstrument(genInstrument());
            writer.setMethodSignature(currentMethodSignature);

            comp_unit.accept(writer);
            out.flush();
            out.close();
        } catch (IOException e) {
            System.err.println("fails to create " + f_name);
        } catch (ParseTreeException e) {
            System.err.println("errors during printing " + f_name);
            e.printStackTrace();
        }
    }
}
