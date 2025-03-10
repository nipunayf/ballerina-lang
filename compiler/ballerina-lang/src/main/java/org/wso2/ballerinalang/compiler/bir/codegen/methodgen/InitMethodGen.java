/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.ballerinalang.compiler.bir.codegen.methodgen;

import io.ballerina.identifier.Utils;
import org.ballerinalang.model.elements.PackageID;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.wso2.ballerinalang.compiler.bir.codegen.JvmCastGen;
import org.wso2.ballerinalang.compiler.bir.codegen.JvmCodeGenUtil;
import org.wso2.ballerinalang.compiler.bir.codegen.JvmPackageGen;
import org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures;
import org.wso2.ballerinalang.compiler.bir.codegen.internal.AsyncDataCollector;
import org.wso2.ballerinalang.compiler.bir.codegen.internal.JavaClass;
import org.wso2.ballerinalang.compiler.bir.codegen.model.BIRFunctionWrapper;
import org.wso2.ballerinalang.compiler.bir.codegen.model.JIMethodCLICall;
import org.wso2.ballerinalang.compiler.bir.codegen.split.JvmConstantsGen;
import org.wso2.ballerinalang.compiler.bir.model.BIRNode;
import org.wso2.ballerinalang.compiler.bir.model.BIRNonTerminator;
import org.wso2.ballerinalang.compiler.bir.model.BIROperand;
import org.wso2.ballerinalang.compiler.bir.model.BIRTerminator;
import org.wso2.ballerinalang.compiler.bir.model.InstructionKind;
import org.wso2.ballerinalang.compiler.bir.model.VarKind;
import org.wso2.ballerinalang.compiler.bir.model.VarScope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.util.Name;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.ballerinalang.model.symbols.SymbolOrigin.VIRTUAL;
import static org.objectweb.asm.Opcodes.AALOAD;
import static org.objectweb.asm.Opcodes.AASTORE;
import static org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ACONST_NULL;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ANEWARRAY;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.BIPUSH;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.ICONST_0;
import static org.objectweb.asm.Opcodes.ICONST_1;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.BAL_RUNTIME;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.CLI_SPEC;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.CREATE_TYPES_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.CURRENT_MODULE_INIT_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.CURRENT_MODULE_STOP_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.GET_TEST_EXECUTION_STATE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.HANDLE_FUTURE_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.JVM_INIT_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.LAMBDA_PREFIX;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MAIN_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_EXECUTE_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_INIT_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_START_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.MODULE_STOP_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.OBJECT;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.PREDEFINED_TYPES;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.RUNTIME_REGISTRY_CLASS;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.RUNTIME_REGISTRY_VARIABLE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.RUNTIME_UTILS;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.SCHEDULER;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.SCHEDULER_VARIABLE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.START_ISOLATED_WORKER;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.STRAND_CLASS;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.TEST_EXECUTE_METHOD;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.TEST_EXECUTION_STATE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmConstants.VALUE_CREATOR;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.ADD_VALUE_CREATOR;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.CURRENT_MODULE_INIT;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.CURRENT_MODULE_STOP;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.GET_MAIN_ARGS;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.GET_RUNTIME_REGISTRY;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.GET_SCHEDULER;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.HANDLE_FUTURE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.INIT_CLASS_CONSTRUCTOR;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.LOAD_NULL_TYPE;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.MODULE_STOP;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.PASS_OBJECT_ARRAY_RETURN_OBJECT;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.PASS_STRAND;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.SCHEDULE_CALL;
import static org.wso2.ballerinalang.compiler.bir.codegen.JvmSignatures.VOID_METHOD_DESC;
import static org.wso2.ballerinalang.compiler.util.CompilerUtils.getMajorVersion;

/**
 * Generates Jvm byte code for the init methods.
 *
 * @since 2.0.0
 */
public class InitMethodGen {

    private final SymbolTable symbolTable;
    private final BUnionType errorOrNilType;
    private int nextId = 0;
    private int nextVarId = 0;

    public InitMethodGen(SymbolTable symbolTable) {
        this.symbolTable = symbolTable;
        this.errorOrNilType =
                BUnionType.create(symbolTable.typeEnv(), null, symbolTable.errorType, symbolTable.nilType);
    }

    /**
     * Generate a lambda function to invoke ballerina main.
     *
     * @param cw        class visitor
     * @param pkg       bir package
     * @param initClass module init class
     */
    public void generateLambdaForPackageInit(ClassWriter cw, BIRNode.BIRPackage pkg, String initClass) {
        //need to generate lambda for package Init as well, if exist
        if (!MethodGenUtils.hasInitFunction(pkg)) {
            return;
        }
        String funcName = MethodGenUtils.calculateLambdaStopFuncName(pkg.packageID);
        MethodVisitor mv = visitFunction(cw, funcName);
        invokeStopFunction(initClass, mv, funcName);
    }

    public void generateLambdaForModuleExecuteFunction(ClassWriter cw, String initClass, JvmCastGen jvmCastGen,
                                                       BIRNode.BIRFunction mainFunc,
                                                       BIRNode.BIRFunction testExecuteFunc) {
        String lambdaFuncName = LAMBDA_PREFIX + MODULE_EXECUTE_METHOD + "$";
        MethodVisitor mv = visitFunction(cw, lambdaFuncName);
        mv.visitCode();
        String methodDesc;
        //load strand as first arg
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, STRAND_CLASS);

        if (mainFunc == null && testExecuteFunc == null) {
            methodDesc = JvmSignatures.MODULE_START;
        } else {
            List<BType> paramTypes;
            BType returnType;

            if (mainFunc != null) {
                paramTypes = Collections.singletonList(symbolTable.anyType);
                returnType = mainFunc.type.retType;
            } else {
                paramTypes = testExecuteFunc.type.paramTypes;
                returnType = testExecuteFunc.type.retType;
            }
            int paramIndex = 1;
            for (BType paramType : paramTypes) {
                mv.visitVarInsn(ALOAD, 0);
                mv.visitIntInsn(BIPUSH, paramIndex);
                mv.visitInsn(AALOAD);
                jvmCastGen.addUnboxInsn(mv, paramType);
                paramIndex += 1;
            }
            methodDesc = JvmCodeGenUtil.getMethodDesc(symbolTable.typeEnv(), paramTypes, returnType);
        }

        mv.visitMethodInsn(INVOKESTATIC, initClass, MODULE_EXECUTE_METHOD, methodDesc, false);
        jvmCastGen.addBoxInsn(mv, errorOrNilType);
        MethodGenUtils.visitReturn(mv, lambdaFuncName, initClass);
    }

    private MethodVisitor visitFunction(ClassWriter cw, String funcName) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, funcName, PASS_OBJECT_ARRAY_RETURN_OBJECT,
                null, null);
        mv.visitCode();
        return mv;
    }

    private void invokeStopFunction(String initClass, MethodVisitor mv, String methodName) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(DUP);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, STRAND_CLASS);
        String stopFuncName = MethodGenUtils.encodeModuleSpecialFuncName(MethodGenUtils.STOP_FUNCTION_SUFFIX);
        mv.visitMethodInsn(INVOKESTATIC, initClass, stopFuncName, JvmSignatures.MODULE_START,
                           false);
        MethodGenUtils.visitReturn(mv, methodName, initClass);
    }

    public void generateModuleInitializer(ClassWriter cw, BIRNode.BIRPackage module, String typeOwnerClass,
                                          String moduleInitClass) {
        // Using object return type since this is similar to a ballerina function without a return.
        // A ballerina function with no returns is equivalent to a function with nil-return.
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, CURRENT_MODULE_INIT_METHOD,
                CURRENT_MODULE_INIT, null, null);
        mv.visitCode();
        mv.visitMethodInsn(INVOKESTATIC, moduleInitClass, CREATE_TYPES_METHOD, VOID_METHOD_DESC, false);
        mv.visitTypeInsn(NEW, typeOwnerClass);
        mv.visitInsn(DUP);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, typeOwnerClass, JVM_INIT_METHOD, INIT_CLASS_CONSTRUCTOR, false);
        mv.visitVarInsn(ASTORE, 1);
        mv.visitLdcInsn(Utils.decodeIdentifier(module.packageID.orgName.getValue()));
        mv.visitLdcInsn(Utils.decodeIdentifier(module.packageID.name.getValue()));
        mv.visitLdcInsn(getMajorVersion(module.packageID.version.getValue()));
        if (module.packageID.isTestPkg) {
            mv.visitInsn(ICONST_1);
        } else {
            mv.visitInsn(ICONST_0);
        }
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKESTATIC, VALUE_CREATOR, "addValueCreator", ADD_VALUE_CREATOR, false);

        // Add a nil-return
        mv.visitInsn(ACONST_NULL);
        MethodGenUtils.visitReturn(mv, CURRENT_MODULE_INIT_METHOD, typeOwnerClass);
    }

    public void generateModuleStop(ClassWriter cw, String moduleInitClass, AsyncDataCollector asyncDataCollector,
                                   JvmConstantsGen jvmConstantsGen) {
        // Using object return type since this is similar to a ballerina function without a return.
        // A ballerina function with no returns is equivalent to a function with nil-return.
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_STATIC, CURRENT_MODULE_STOP_METHOD, CURRENT_MODULE_STOP,
                null, null);
        mv.visitCode();
        generateGetSchedulerVar(mv);
        String lambdaName = generateStopDynamicLambdaBody(cw, moduleInitClass);
        generateCallStopDynamicLambda(mv, lambdaName, moduleInitClass);

        mv.visitVarInsn(ALOAD, 1);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESTATIC, moduleInitClass, MODULE_STOP_METHOD, MODULE_STOP, false);
        mv.visitInsn(RETURN);
        JvmCodeGenUtil.visitMaxStackForMethod(mv, CURRENT_MODULE_STOP_METHOD, moduleInitClass);
        mv.visitEnd();
    }

    private static void generateGetSchedulerVar(MethodVisitor mv) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, BAL_RUNTIME, SCHEDULER_VARIABLE, GET_SCHEDULER);
        mv.visitVarInsn(ASTORE, 1);
    }

    private String generateStopDynamicLambdaBody(ClassWriter cw, String initClass) {
        String lambdaName = LAMBDA_PREFIX + "stopdynamic";
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC + ACC_STATIC, lambdaName, PASS_OBJECT_ARRAY_RETURN_OBJECT,
                null, null);
        mv.visitCode();
        generateCallSchedulerStopDynamicListeners(mv, lambdaName, initClass);
        return lambdaName;
    }

    private void generateCallSchedulerStopDynamicListeners(MethodVisitor mv, String lambdaName, String initClass) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_1);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, RUNTIME_REGISTRY_CLASS);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(ICONST_0);
        mv.visitInsn(AALOAD);
        mv.visitTypeInsn(CHECKCAST, STRAND_CLASS);
        mv.visitMethodInsn(INVOKEVIRTUAL, RUNTIME_REGISTRY_CLASS, "gracefulStop", PASS_STRAND, false);
        mv.visitInsn(ACONST_NULL);
        MethodGenUtils.visitReturn(mv, lambdaName, initClass);
    }

    private void generateCallStopDynamicLambda(MethodVisitor mv, String lambdaName, String moduleInitClass) {
        addRuntimeRegistryAsParameter(mv);
        generateMethodBody(mv, moduleInitClass, lambdaName);
        // handle future result
        mv.visitVarInsn(ALOAD, 3);
        mv.visitMethodInsn(INVOKESTATIC, RUNTIME_UTILS, HANDLE_FUTURE_METHOD, HANDLE_FUTURE, false);
    }

    private void addRuntimeRegistryAsParameter(MethodVisitor mv) {
        mv.visitIntInsn(BIPUSH, 2);
        mv.visitTypeInsn(ANEWARRAY, OBJECT);
        mv.visitVarInsn(ASTORE, 2);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitInsn(ICONST_1);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, BAL_RUNTIME, RUNTIME_REGISTRY_VARIABLE, GET_RUNTIME_REGISTRY);
        mv.visitInsn(AASTORE);
    }

    private void generateMethodBody(MethodVisitor mv, String initClass, String stopFuncName) {
        mv.visitVarInsn(ALOAD, 1);
        JvmCodeGenUtil.createFunctionPointer(mv, initClass, stopFuncName);
        mv.visitInsn(ACONST_NULL);
        mv.visitFieldInsn(GETSTATIC, PREDEFINED_TYPES, "TYPE_NULL", LOAD_NULL_TYPE);
        mv.visitLdcInsn("stop");
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEVIRTUAL, SCHEDULER, START_ISOLATED_WORKER, SCHEDULE_CALL, false);
        mv.visitVarInsn(ASTORE, 3);
    }

    public void enrichPkgWithInitializers(Map<String, BIRFunctionWrapper> birFunctionMap,
                                          Map<String, JavaClass> jvmClassMap, String typeOwnerClass,
                                          BIRNode.BIRPackage pkg, Set<PackageID> moduleImports,
                                          BIRNode.BIRFunction mainFunc,
                                          BIRNode.BIRFunction testExecuteFunc) {
        JavaClass javaClass = jvmClassMap.get(typeOwnerClass);
        BIRNode.BIRFunction initFunc = generateDefaultFunction(moduleImports, pkg, MODULE_INIT_METHOD,
                MethodGenUtils.INIT_FUNCTION_SUFFIX);
        javaClass.functions.add(initFunc);
        pkg.functions.add(initFunc);
        birFunctionMap.put(JvmCodeGenUtil.getPackageName(pkg.packageID) + MODULE_INIT_METHOD,
                JvmPackageGen.getFunctionWrapper(symbolTable.typeEnv(), initFunc, pkg.packageID, typeOwnerClass));

        BIRNode.BIRFunction startFunc = generateDefaultFunction(moduleImports, pkg, MODULE_START_METHOD,
                                                                MethodGenUtils.START_FUNCTION_SUFFIX);
        javaClass.functions.add(startFunc);
        pkg.functions.add(startFunc);
        birFunctionMap.put(JvmCodeGenUtil.getPackageName(pkg.packageID) + MODULE_START_METHOD,
                JvmPackageGen.getFunctionWrapper(symbolTable.typeEnv(), startFunc, pkg.packageID, typeOwnerClass));

        BIRNode.BIRFunction execFunc = generateExecuteFunction(pkg, mainFunc, testExecuteFunc
                                                              );
        javaClass.functions.add(execFunc);
        pkg.functions.add(execFunc);
        birFunctionMap.put(JvmCodeGenUtil.getPackageName(pkg.packageID) + MODULE_EXECUTE_METHOD,
                JvmPackageGen.getFunctionWrapper(symbolTable.typeEnv(), execFunc, pkg.packageID, typeOwnerClass));
    }

    private BIRNode.BIRFunction generateExecuteFunction(BIRNode.BIRPackage pkg,
                                                        BIRNode.BIRFunction mainFunc,
                                                        BIRNode.BIRFunction testExecuteFunc) {
        BIRNode.BIRVariableDcl retVar = new BIRNode.BIRVariableDcl(null, errorOrNilType,
                new Name("%ret"), VarScope.FUNCTION, VarKind.RETURN, null);
        BIROperand retVarRef = new BIROperand(retVar);
        List<BIROperand> functionArgs = new ArrayList<>();
        BInvokableType funcType =
                new BInvokableType(symbolTable.typeEnv(), Collections.emptyList(), null, errorOrNilType, null);
        BIRNode.BIRFunction modExecFunc = new BIRNode.BIRFunction(null, new Name(MODULE_EXECUTE_METHOD),
                0, funcType, null, 0, VIRTUAL);
        List<BType> paramTypes = new ArrayList<>();
        List<BIRNode.BIRFunctionParameter> parameters = new ArrayList<>();
        List<BIRNode.BIRParameter> requiredParameters = new ArrayList<>();
        int argsCount = 0;
        int defaultParamCount = 0;
        BIRNode.BIRFunctionParameter cliArgVar;
        List<BIROperand> defaultableArgRefs = new ArrayList<>();

        if (mainFunc != null) {
            List<BIRNode.BIRFunctionParameter> mainParameters = mainFunc.parameters;
            Name argName = new Name("%_cli");
            cliArgVar = new BIRNode.BIRFunctionParameter(null, symbolTable.anyType, argName, VarScope.FUNCTION,
                    VarKind.ARG, null, false, false);
            paramTypes.add(symbolTable.anyType);
            parameters.add(cliArgVar);
            requiredParameters.add(new BIRNode.BIRParameter(null, argName, 0));
            modExecFunc.localVars.add(cliArgVar);
            argsCount += 1;

            for (BIRNode.BIRFunctionParameter param : mainParameters) {
                BIRNode.BIRVariableDcl paramVar = new BIRNode.BIRVariableDcl(null, param.type,
                        new Name("%param" + param.jvmVarName), VarScope.FUNCTION, VarKind.LOCAL, null);
                BIROperand varRef = new BIROperand(paramVar);
                modExecFunc.localVars.add(paramVar);
                functionArgs.add(varRef);

                // Create temporary variables for main args if they have default expr
                if (param.hasDefaultExpr) {
                    BIRNode.BIRVariableDcl tempParamVar = new BIRNode.BIRVariableDcl(null, symbolTable.anyType,
                            new Name("%tempVar" + param.jvmVarName), VarScope.FUNCTION, VarKind.TEMP, null);
                    BIROperand tempVarRef = new BIROperand(tempParamVar);
                    modExecFunc.localVars.add(tempParamVar);
                    defaultableArgRefs.add(tempVarRef);
                    defaultParamCount += 1;
                }
            }
        }

        if (testExecuteFunc != null) {
            paramTypes = testExecuteFunc.type.paramTypes;
            parameters = testExecuteFunc.parameters;
            requiredParameters = testExecuteFunc.requiredParams;
            argsCount += modExecFunc.parameters.size();
            for (BIRNode.BIRFunctionParameter param : parameters) {
                BIRNode.BIRVariableDcl paramVar = new BIRNode.BIRVariableDcl(null, param.type,
                        new Name("%param" + param.jvmVarName), VarScope.FUNCTION, VarKind.ARG, null);
                BIROperand varRef = new BIROperand(paramVar);
                modExecFunc.localVars.add(paramVar);
                functionArgs.add(varRef);
            }
        }

        funcType.paramTypes = paramTypes;
        modExecFunc.parameters = parameters;
        modExecFunc.requiredParams = requiredParameters;
        modExecFunc.argsCount = argsCount;

        modExecFunc.localVars.add(retVar);
        addAndGetNextBasicBlock(modExecFunc);
        BIRNode.BIRVariableDcl boolVal = addAndGetNextVar(modExecFunc, symbolTable.booleanType);
        BIROperand boolRef = new BIROperand(boolVal);
        addCheckedInvocation(modExecFunc, pkg.packageID, MODULE_INIT_METHOD, retVarRef, boolRef);

        if (mainFunc != null) {
            injectCLIArgInvocation(modExecFunc, functionArgs, defaultableArgRefs);
            injectDefaultArgs(functionArgs, mainFunc, modExecFunc, boolRef, pkg.globalVars, defaultableArgRefs,
                    defaultParamCount);
            addCheckedInvocationWithArgs(modExecFunc, pkg.packageID, MAIN_METHOD, retVarRef, boolRef,
                    functionArgs, mainFunc.annotAttachments);
        }

        BIRNode.BIRBasicBlock lastBB = addCheckedInvocation(modExecFunc, pkg.packageID, MODULE_START_METHOD,
                retVarRef, boolRef);

        if (testExecuteFunc != null) {
            lastBB = addTestExecuteInvocationWithGracefulExitCall(modExecFunc, pkg.packageID, retVarRef, functionArgs,
                                                                  Collections.emptyList());
        }
        lastBB.terminator = new BIRTerminator.Return(null);
        return modExecFunc;
    }

    private void injectCLIArgInvocation(BIRNode.BIRFunction modExecFunc, List<BIROperand> functionArgs,
                                        List<BIROperand> tempFuncArgs) {
        BIRNode.BIRBasicBlock lastBB = modExecFunc.basicBlocks.getLast();
        JIMethodCLICall jiMethodCall = new JIMethodCLICall(null);
        jiMethodCall.lhsArgs = functionArgs;
        jiMethodCall.jClassName = CLI_SPEC;
        jiMethodCall.jMethodVMSig = GET_MAIN_ARGS;
        jiMethodCall.name = "getMainArgs";
        jiMethodCall.thenBB = addAndGetNextBasicBlock(modExecFunc);
        jiMethodCall.defaultFunctionArgs = tempFuncArgs;
        lastBB.terminator = jiMethodCall;
    }

    private void injectDefaultArgs(List<BIROperand> mainArgs, BIRNode.BIRFunction mainFunc,
                                   BIRNode.BIRFunction modExecFunc, BIROperand boolRef,
                                   List<BIRNode.BIRGlobalVariableDcl> globalVars, List<BIROperand> tempFuncArgs,
                                   int defaultParamCount) {
        for (int i = 0; i < tempFuncArgs.size(); i++) {
            int mainFuncParamIndex = mainFunc.parameters.size() - defaultParamCount + i;
            BIRNode.BIRFunctionParameter parameter = mainFunc.parameters.get(mainFuncParamIndex);
            BIRNode.BIRBasicBlock lastBB = modExecFunc.basicBlocks.getLast();
            BIROperand argOperand = mainArgs.get(mainFuncParamIndex);
            BIROperand tempArgOperand = tempFuncArgs.get(i);
            BIRNonTerminator.TypeTest typeTest =
                    new BIRNonTerminator.TypeTest(null, symbolTable.nilType, boolRef, tempArgOperand);
            lastBB.instructions.add(typeTest);
            BIRNode.BIRBasicBlock trueBB = addAndGetNextBasicBlock(modExecFunc);
            BIRNode.BIRBasicBlock falseBB = addAndGetNextBasicBlock(modExecFunc);
            BIRNode.BIRBasicBlock nextBB = addAndGetNextBasicBlock(modExecFunc);
            lastBB.terminator = new BIRTerminator.Branch(null, boolRef, trueBB, falseBB);
            BIRNonTerminator.TypeCast typeCast = new BIRNonTerminator.TypeCast(null, argOperand,
                    tempArgOperand, argOperand.variableDcl.type, false);
            BInvokableSymbol defaultFunc =
                    ((BInvokableTypeSymbol) mainFunc.type.tsymbol).defaultValues.get(parameter.metaVarName);
            trueBB.terminator = getFPCallForDefaultParameter(defaultFunc, argOperand, nextBB, globalVars,
                    mainArgs.subList(0, mainFuncParamIndex));
            falseBB.instructions.add(typeCast);
            falseBB.terminator = new BIRTerminator.GOTO(null, nextBB);
        }
    }

    private BIRTerminator.FPCall getFPCallForDefaultParameter(BInvokableSymbol defaultFunc, BIROperand argOperand,
                                                              BIRNode.BIRBasicBlock thenBB,
                                                              List<BIRNode.BIRGlobalVariableDcl> globalVars,
                                                              List<BIROperand> args) {
        BIRNode.BIRGlobalVariableDcl defaultFuncVar = getDefaultFuncFPGlobalVar(defaultFunc.name, globalVars);
        BIROperand defaultFP = new BIROperand(defaultFuncVar);
        return new BIRTerminator.FPCall(null, InstructionKind.FP_CALL, defaultFP, args, argOperand, false, thenBB,
                null, new ArrayList<>());
    }

    private BIRNode.BIRGlobalVariableDcl getDefaultFuncFPGlobalVar(Name name,
                                                                   List<BIRNode.BIRGlobalVariableDcl> globalVars) {

        for (BIRNode.BIRGlobalVariableDcl globalVar : globalVars) {
            if (globalVar.name.value.equals(name.value)) {
                return globalVar;
            }
        }
        return null;
    }

    private BIRNode.BIRFunction generateDefaultFunction(Set<PackageID> imprtMods, BIRNode.BIRPackage pkg,
                                                        String funcName, String initName) {
        nextId = 0;
        nextVarId = 0;

        BIRNode.BIRVariableDcl retVar = new BIRNode.BIRVariableDcl(null, errorOrNilType, new Name("%ret"),
                                                                   VarScope.FUNCTION, VarKind.RETURN, null);
        BIROperand retVarRef = new BIROperand(retVar);

        BInvokableType funcType =
                new BInvokableType(symbolTable.typeEnv(), Collections.emptyList(), null, errorOrNilType, null);
        BIRNode.BIRFunction modInitFunc = new BIRNode.BIRFunction(symbolTable.builtinPos, new Name(funcName), 0,
                funcType, null, 0, VIRTUAL);
        modInitFunc.localVars.add(retVar);
        addAndGetNextBasicBlock(modInitFunc);

        BIRNode.BIRVariableDcl boolVal = addAndGetNextVar(modInitFunc, symbolTable.booleanType);
        BIROperand boolRef = new BIROperand(boolVal);
        String initFuncName = MethodGenUtils.encodeModuleSpecialFuncName(funcName);
        for (PackageID id : imprtMods) {
            addCheckedInvocation(modInitFunc, id, initFuncName, retVarRef, boolRef);
        }

        String currentInitFuncName = MethodGenUtils.encodeModuleSpecialFuncName(initName);
        BIRNode.BIRBasicBlock lastBB = addCheckedInvocation(modInitFunc, pkg.packageID, currentInitFuncName, retVarRef,
                                                            boolRef);

        lastBB.terminator = new BIRTerminator.Return(null);

        return modInitFunc;
    }

    private BIRNode.BIRVariableDcl addAndGetNextVar(BIRNode.BIRFunction func, BType typeVal) {
        BIRNode.BIRVariableDcl nextLocalVar = new BIRNode.BIRVariableDcl(typeVal, getNextVarId(), VarScope.FUNCTION,
                                                                         VarKind.LOCAL);
        func.localVars.add(nextLocalVar);
        return nextLocalVar;
    }

    private Name getNextVarId() {
        String varIdPrefix = "%";
        nextVarId += 1;
        return new Name(varIdPrefix + nextVarId);
    }

    private BIRNode.BIRBasicBlock addTestExecuteInvocationWithGracefulExitCall(BIRNode.BIRFunction func,
                                                                               PackageID modId, BIROperand retVar,
                                                                               List<BIROperand> args,
                                                                               List<BIRNode.BIRAnnotationAttachment>
                                                                                       calleeAnnotAttachments) {
        BIRNode.BIRBasicBlock lastBB = func.basicBlocks.getLast();
        BIRNode.BIRBasicBlock nextBB = addAndGetNextBasicBlock(func);
        if (JvmCodeGenUtil.isBuiltInPackage(modId)) {
            lastBB.terminator = new BIRTerminator.Call(null, InstructionKind.CALL, false, modId,
                    new Name(TEST_EXECUTE_METHOD), args, null, nextBB, calleeAnnotAttachments, Collections.emptySet());
            return nextBB;
        }
        lastBB.terminator = new BIRTerminator.Call(null, InstructionKind.CALL, false, modId,
                new Name(TEST_EXECUTE_METHOD), args, retVar, nextBB, calleeAnnotAttachments, Collections.emptySet());
        return nextBB;
    }

    private BIRNode.BIRBasicBlock addCheckedInvocationWithArgs(BIRNode.BIRFunction func, PackageID modId,
                                                               String initFuncName, BIROperand retVar,
                                                               BIROperand boolRef,
                                                               List<BIROperand> args,
                                                               List<BIRNode.BIRAnnotationAttachment>
                                                                       calleeAnnotAttachments) {
        BIRNode.BIRBasicBlock lastBB = func.basicBlocks.getLast();
        BIRNode.BIRBasicBlock nextBB = addAndGetNextBasicBlock(func);
        // TODO remove once lang.annotation is fixed
        if (JvmCodeGenUtil.isBuiltInPackage(modId)) {
            lastBB.terminator = new BIRTerminator.Call(null, InstructionKind.CALL, false, modId,
                    new Name(initFuncName), args, null, nextBB, calleeAnnotAttachments, Collections.emptySet());
            return nextBB;
        }
        lastBB.terminator = new BIRTerminator.Call(null, InstructionKind.CALL, false, modId, new Name(initFuncName),
                args, retVar, nextBB, calleeAnnotAttachments, Collections.emptySet());

        BIRNonTerminator.TypeTest typeTest = new BIRNonTerminator.TypeTest(null, symbolTable.errorType, boolRef,
                retVar);
        nextBB.instructions.add(typeTest);

        BIRNode.BIRBasicBlock trueBB = addAndGetNextBasicBlock(func);
        BIRNode.BIRBasicBlock retBB = addAndGetNextBasicBlock(func);
        retBB.terminator = new BIRTerminator.Return(null);
        trueBB.terminator = new BIRTerminator.GOTO(null, retBB);

        BIRNode.BIRBasicBlock falseBB = addAndGetNextBasicBlock(func);
        nextBB.terminator = new BIRTerminator.Branch(null, boolRef, trueBB, falseBB);
        return falseBB;
    }

    private BIRNode.BIRBasicBlock addCheckedInvocation(BIRNode.BIRFunction func, PackageID modId, String initFuncName,
                                                       BIROperand retVar, BIROperand boolRef) {
        return addCheckedInvocationWithArgs(func, modId, initFuncName, retVar, boolRef, Collections.emptyList(),
                Collections.emptyList());
    }

    private BIRNode.BIRBasicBlock addAndGetNextBasicBlock(BIRNode.BIRFunction func) {
        BIRNode.BIRBasicBlock nextbb = new BIRNode.BIRBasicBlock("genBB", incrementAndGetNextId());
        func.basicBlocks.add(nextbb);
        return nextbb;
    }

    public void resetIds() {
        nextId = 0;
        nextVarId = 0;
    }

    public int incrementAndGetNextId() {
        return nextId++;
    }

    public void generateGetTestExecutionState(ClassWriter cw, String className) {
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC | ACC_STATIC, GET_TEST_EXECUTION_STATE, "()J",
                null, null);
        mv.visitCode();
        mv.visitFieldInsn(GETSTATIC, className, TEST_EXECUTION_STATE, "J");
        mv.visitInsn(LRETURN);
        JvmCodeGenUtil.visitMaxStackForMethod(mv, GET_TEST_EXECUTION_STATE, className);
        mv.visitEnd();
    }
}
