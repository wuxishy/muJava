/**
 * Copyright (C) 2016 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.util.*;

import mujava.MutationSystem;
import openjava.ptree.Expression;

/**
 * <p>Recursively evaluate an expression and generate instrumentation to a stack</p>
 * <p>
 * Refer to <a href=https://www.sharelatex.com/project/577c5c8abfe51f234ad0c329>the planning document</a>
 * for exact details on the recursive parser
 * </p>
 * @author Haoyuan Sun
 * @version 0.1a
 */

public abstract class InstrumentationParser extends InstrumentationMutator{
    public InstrumentationParser(FileEnvironment file_env, CompilationUnit comp_unit) {
        super(file_env, comp_unit);

        typeStack = new Stack<OJClass>();
        exprStack = new Stack<Expression>();
        post = new StatementList();
    }

    // count the number of extra variables being used
    protected int counter = 0;

    // the mutated expression to compare
    protected int comp = 0;

    // keep the operands of the expression in a stack
    // the type of each statement
    protected Stack<OJClass> typeStack;
    // the main body of each expression
    protected Stack<Expression> exprStack;

    protected StatementList post;

    public void visit(ArrayAccess p) throws ParseTreeException{
        if (mutExpression == null) mutExpression = p;

        typeStack.add(getType(p));
        exprStack.add(null);
        typeStack.add(getType(p));
        exprStack.add(null);

        counter += 2;

        ExpressionList origInd = new ExpressionList();
        ExpressionList mutInd = new ExpressionList();
        int dim = 0;
        Expression ref = p;
        do{
            origInd.add(genVar(counter + dim));
            mutInd.add(genVar(counter + dim));
            typeStack.add(OJSystem.INT); // array index is always integer
            exprStack.add(((ArrayAccess)ref).getIndexExpr());

            ++dim;

            ref = ((ArrayAccess)ref).getReferenceExpr();
        } while(ref instanceof ArrayAccess);

        counter += dim;

        for(int i = 0; i < dim; ++i){
            Expression cur = exprStack.get(counter-dim+i);

            origInd.set(i, genVar(counter));
            mutInd.set(i, genVar(counter+1));
            exprStack.set(counter-dim+i, null);

            exprStack.set(counter-dim-2, reconstructArrayAccess(ref, origInd));
            exprStack.set(counter-dim-1, reconstructArrayAccess(ref, mutInd));

            cur.accept(this);

            origInd.set(i, genVar(counter-dim+i));
            mutInd.set(i, genVar(counter-dim+i));
            exprStack.set(counter-dim+i, cur);
        }

        pop(dim + 2);

        if(mutExpression.getObjectID() == p.getObjectID()) mutExpression = null;
    }

    public void visit(AssignmentExpression p) throws ParseTreeException {
        if(mutExpression == null) mutExpression = p;

        // assign the previous value to LHS
        post.add(new ExpressionStatement(
                new AssignmentExpression(p.getLeft(), p.getOperator(), genVar(counter))));

        // look for RHS
        // left assignments will not be examined
        // -- Haoyuan Feb 2017
        p.getRight().accept(this);

        post.remove(post.size()-1);

        if (mutExpression.getObjectID() == p.getObjectID()) mutExpression = null;
    }

    public void visit(BinaryExpression p) throws ParseTreeException {
        if (mutExpression == null) mutExpression = p;

        // original
        typeStack.add(getType(p));
        exprStack.add(new BinaryExpression(genVar(counter+3), p.getOperator(), genVar(counter+2))); // +0
        // mutant
        typeStack.add(getType(p));
        exprStack.add(new BinaryExpression(genVar(counter+4), p.getOperator(), genVar(counter+2))); // +1
        // RHS
        typeStack.add(getType(p.getRight()));
        exprStack.add(p.getRight()); // +2

        counter += 3;
        p.getLeft().accept(this);

        pop(3);

        // original
        typeStack.add(getType(p));
        exprStack.add(new BinaryExpression(genVar(counter+2), p.getOperator(), genVar(counter+3))); // +0
        // mutant
        typeStack.add(getType(p));
        exprStack.add(new BinaryExpression(genVar(counter+2), p.getOperator(), genVar(counter+4))); // +1
        // LHS
        typeStack.add(getType(p.getLeft()));
        exprStack.add(p.getLeft()); // +2

        counter += 3;
        p.getRight().accept(this);

        pop(3);

        if (mutExpression.getObjectID() == p.getObjectID()) mutExpression = null;
    }

    public void visit(MethodCall p) throws ParseTreeException {
        if(mutExpression == null) mutExpression = p;

        // the method is static if the reference is a type instead of expression
        boolean isStatic = (p.getReferenceExpr() == null);
        // list of arguments
        ExpressionList args = p.getArguments();
        // number of arguments in this method
        int arg_size = args.size();

        // replace the list of arguments with tmp variables
        ExpressionList origArgs = new ExpressionList();
        ExpressionList mutArgs = new ExpressionList();
        for(int i = 0; i < arg_size; ++i){
            Variable tmp = genVar(counter+2+i);
            origArgs.add(tmp);
            mutArgs.add(tmp);
        }

        // original
        MethodCall orig = isStatic ? new MethodCall(p.getReferenceType(), p.getName(), origArgs) :
                                     new MethodCall(p.getReferenceExpr(), p.getName(), origArgs);
        typeStack.add(getType(p));
        exprStack.add(orig); // +0
        // mutant
        MethodCall mut = isStatic ? new MethodCall(p.getReferenceType(), p.getName(), mutArgs) :
                                    new MethodCall(p.getReferenceExpr(), p.getName(), mutArgs);
        typeStack.add(getType(p));
        exprStack.add(mut); // +1

        // assign the original list of arguments to tmp variables
        for(int i = 0; i < arg_size; ++i){
            typeStack.add(getType(args.get(i)));
            exprStack.add(args.get(i));
        }
        //System.out.println(exprStack.size());
        //System.out.println(counter);
        counter += arg_size + 2;

        // more mutants may be in the reference expression
        if(!isStatic) p.getReferenceExpr().accept(this);

        // mutants in the arguments
        Variable tmpvar = null;
        Expression tmpexpr = null;

        Variable nextOrig = genVar(counter);
        Variable nextMut = genVar(counter+1);
        for(int i = 0; i < arg_size; ++i){
            // mutate
            tmpvar = (Variable)origArgs.get(i);
            tmpexpr = exprStack.get(counter-arg_size+i);

            origArgs.set(i, nextOrig);
            mutArgs.set(i, nextMut);
            exprStack.set(counter-arg_size+i, null);

            args.get(i).accept(this);

            // restore
            origArgs.set(i, tmpvar);
            mutArgs.set(i, tmpvar);
            exprStack.set(counter-arg_size+i, tmpexpr);
        }

        pop(arg_size + 2);

        if (mutExpression.getObjectID() == p.getObjectID()) mutExpression = null;
    }

    public void visit(UnaryExpression p) throws ParseTreeException{
        if(mutExpression == null) mutExpression = p;

        int addlines = 0;

        if(p.getOperator() >= 4) {
            typeStack.add(getType(p));
            exprStack.add(new UnaryExpression(genVar(counter+2), p.getOperator())); //+0
            typeStack.add(getType(p));
            exprStack.add(new UnaryExpression(genVar(counter+3), p.getOperator())); //+1

            addlines = 2;

            counter += addlines;

            p.getExpression().accept(this);
        }
        // short-cut operators can only operate on variables and array elements
        // but no mutants can be generated for variables
        else if (p.getExpression() instanceof ArrayAccess){
            typeStack.add(getType(p));
            typeStack.add(getType(p));

            if(p.getOperator() == UnaryExpression.PRE_INCREMENT){
                exprStack.add(new BinaryExpression(genVar(counter+2), "+", Literal.constantOne()));
                exprStack.add(new BinaryExpression(genVar(counter+3), "+", Literal.constantOne()));
            }
            else if(p.getOperator() == UnaryExpression.PRE_DECREMENT){
                exprStack.add(new BinaryExpression(genVar(counter+2), "-", Literal.constantOne()));
                exprStack.add(new BinaryExpression(genVar(counter+3), "-", Literal.constantOne()));
            }
            else if(p.getOperator() == UnaryExpression.POST_INCREMENT){
                exprStack.add(new BinaryExpression(genVar(counter+2), "+", Literal.constantZero()));
                exprStack.add(new BinaryExpression(genVar(counter+3), "+", Literal.constantZero()));
            }
            else if(p.getOperator() == UnaryExpression.POST_DECREMENT){
                exprStack.add(new BinaryExpression(genVar(counter+2), "-", Literal.constantZero()));
                exprStack.add(new BinaryExpression(genVar(counter+3), "-", Literal.constantZero()));
            }

            typeStack.add(getType(p));
            exprStack.add(null); // +2
            typeStack.add(getType(p));
            exprStack.add(null); // +3

            addlines += 4;
            counter += 4;

            ExpressionList origInd = new ExpressionList();
            ExpressionList mutInd = new ExpressionList();
            int dim = 0;
            Expression ref = p.getExpression();
            do{
                origInd.add(genVar(counter + dim));
                mutInd.add(genVar(counter + dim));
                typeStack.add(OJSystem.INT); // array index is always integer
                exprStack.add(((ArrayAccess)ref).getIndexExpr());

                ++dim;

                ref = ((ArrayAccess)ref).getReferenceExpr();
            } while(ref instanceof ArrayAccess);

            addlines += dim;
            counter += dim;

            comp = counter;

            post.add(null);

            for(int i = 0; i < dim; ++i){
                Expression cur = exprStack.get(counter-dim+i);

                origInd.set(i, genVar(counter));
                mutInd.set(i, genVar(counter+1));
                exprStack.set(counter-dim+i, null);

                exprStack.set(counter-dim-2, reconstructArrayAccess(ref, origInd));
                exprStack.set(counter-dim-1, reconstructArrayAccess(ref, mutInd));
                post.set(post.size()-1, new ExpressionStatement(
                        new UnaryExpression(p.getOperator(), reconstructArrayAccess(ref, origInd))));

                cur.accept(this);

                origInd.set(i, genVar(counter-dim+i));
                mutInd.set(i, genVar(counter-dim+i));
                exprStack.set(counter-dim+i, cur);
            }

            post.remove(post.size()-1);

            comp = 0;
        }

        pop(addlines);

        if(mutExpression.getObjectID() == p.getObjectID()) mutExpression = null;
    }

    // combine typeStack and exprStack into a list of statements ready to be printed
    public Instrument genInstrument(){
        Instrument inst = new Instrument();
        for(int i = counter-1; i >= 0; --i){
            if(exprStack.get(i) == null) continue;
            inst.init.add(new VariableDeclaration(TypeName.forOJClass(typeStack.get(i)),
                                                  InstConfig.varPrefix+i, exprStack.get(i)));
        }
        inst.addAssertion(InstConfig.varPrefix+comp, InstConfig.varPrefix+(comp+1));
        inst.post = post;
        inst.varName = InstConfig.varPrefix+0;

        return inst;
    }

    // pop the top k elements in the expression stack
    protected void pop(int k){
        for(int i = 0; i < k; ++i){
            typeStack.pop();
            exprStack.pop();
            --counter;
        }
    }

    // generate a variable with name in the form of prefix+number
    protected Variable genVar(int i){
        return new Variable(InstConfig.varPrefix + i);
    }

    // remake ArrayAccess, ind lists indexes in reverse order
    protected ArrayAccess reconstructArrayAccess(Expression ref, ExpressionList ind){
        ArrayAccess ret = new ArrayAccess(ref, ind.get(ind.size()-1));

        for(int i = ind.size()-2; i >= 0; --i) ret = new ArrayAccess(ret, ind.get(i));

        return ret;
    }
}
