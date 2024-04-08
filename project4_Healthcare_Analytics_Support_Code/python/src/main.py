import json 
import sys 
from pathlib import Path
from model_timer import Timer
from ipinstance import IPInstance
import math
import cProfile

def main(filepath : str):
	
	filename = Path(filepath).name
	watch =  Timer()
	watch.start()
	solver = IPInstance(filepath)
	inst_str = solver.toString()
	# best_val, _ = solver.branch_and_bound_dfs()
	best_val, _ = solver.branch_and_bound_best()
	# best_val, _ = solver.branch_and_bound()
	watch.stop()
	print(inst_str)


	sol_dict ={
		"Instance" : filename,
		"Time" : str(round(watch.getElapsed(), 2)),
		"Result" : f"{int(round(best_val, 0))}",
		"Solution" : "OPT" if solver.objVal != None else "ERR"
	}
	print(json.dumps(sol_dict))	

if __name__ == "__main__":
	if len(sys.argv) != 2:
		print("Usage: python main.py <input_file>")
	main(sys.argv[1])
	# cProfile.run('main(sys.argv[1])')