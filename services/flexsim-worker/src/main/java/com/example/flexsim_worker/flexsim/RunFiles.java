package com.example.flexsim_worker.flexsim;

import java.nio.file.Path;

/**
 * The files of ONE run, all in its own folder (for example flexsim-runs\run-7\):
 *   model   = model.fsm            a COPY of the model (4 runs never share one model file)
 *   script  = script.txt           the commands for this run
 *   log     = flexsim-output.log   everything FlexSim prints (look here when a run fails)
 */
public record RunFiles(Path folder, Path model, Path script, Path log) {
}
