from dataclasses import dataclass
import math
import numpy  as np
from docplex.mp.model import Model
import heapq

@dataclass(frozen=True)
class IPConfig:
   numTests: int # number of tests
   numDiseases : int # number of diseases
   costOfTest: np.ndarray #[numTests] the cost of each test
   A : np.ndarray #[numTests][numDiseases] 0/1 matrix if test is positive for disease


#  * File Format
#  * #Tests (i.e., n)
#  * #Diseases (i.e., m)
#  * Cost_1 Cost_2 . . . Cost_n
#  * A(1,1) A(1,2) . . . A(1, m)
#  * A(2,1) A(2,2) . . . A(2, m)
#  * . . . . . . . . . . . . . .
#  * A(n,1) A(n,2) . . . A(n, m)


# each column must be unique from the other columns
# select tests such that each column is unique and minimize the cost
# you can tell if columns are unique by checking if the dot product of the columns is 0 
        # sum of absolute value of differences is 0

def data_parse(filename : str) :
    try:
      with open(filename,"r") as fl:
        numTests = int(fl.readline().strip()) #n 
        numDiseases = int(fl.readline().strip()) #m

        costOfTest = np.array([float(i) for i in fl.readline().strip().split()])

        A = np.zeros((numTests,numDiseases))
        for i in range(0,numTests):
          A[i,:] = np.array([int(i) for i in fl.readline().strip().split() ])
        return numTests,numDiseases,costOfTest,A
    except Exception as e:
       print(f"Error reading instance file. File format may be incorrect.{e}")
       exit(1)

class IPInstance:

  def __init__(self,filename : str) -> None:
    numT,numD,cst,A = data_parse(filename)
    self.numTests = numT
    self.numDiseases = numD
    self.costOfTest = cst
    self.A = A
    self.model = Model() #CPLEX solver
    self.difference_matrix = self.create_difference_matrix()
    self.objVal, self.solution = self.solve()
    self.constraint_map = self.preComputeConstraints()
  
  def create_difference_matrix(self):
    difference_matrix = np.zeros((self.numTests, self.numDiseases * (self.numDiseases - 1) // 2), dtype=int)

    col_index = 0
    for j in range(self.numDiseases):
        for k in range(j + 1, self.numDiseases):
            # XOR each element of the two disease columns and store the result in the difference matrix
            difference_matrix[:, col_index] = np.bitwise_xor(self.A[:, j].astype(int), self.A[:, k].astype(int))
            col_index += 1

    return difference_matrix
  
  def solve(self):
    # 1. Define decision variables
    # x = self.model.integer_var_list(self.numTests, 0, 1, name="x")
    x = self.model.continuous_var_list(self.numTests, 0, 1, name="x")

    # 2. Set objective function (minimize total cost)
    self.model.minimize(self.model.sum(self.costOfTest[i] * x[i] for i in range(self.numTests)))

    # 3. Add differentiation constraints for each disease pair
    for j in range(self.difference_matrix.shape[1]):
      self.model.add_constraint(self.model.sum(x[i] * self.difference_matrix[i, j] for i in range(self.difference_matrix.shape[0])) >= 1)
    
    # 4. Solve the model
    self.model.solve()

    # solution = self.model.solution.get_values(x)
    # print("Solution:", solution)
    # print("Objective value:", self.model.solution.get_objective_value())
    return self.model.solution.get_objective_value(), self.model.solution.get_values(x)
  
  def apply_constraints(self, constraints: dict[int, int]):
    docplex_constraints = []
    for key, val in constraints.items():
    #   if val == 1:
    #     c = self.model.get_var_by_name("x_" + str(key)) >= 1
    #   else:
    #     c = self.model.get_var_by_name("x_" + str(key)) <= 0
    #   docplex_constraints.append(c)
      docplex_constraints.append(self.constraint_map["x_" + str(key)][val])
    self.model.add_constraints(docplex_constraints)
    self.model.solve()

    new_sol = []
    ret_val = math.inf
    if self.model.solution is not None:
      new_sol = [self.model.solution.get_value(var) for var in self.model.iter_variables()]
      # new_sol = self.model.get_all_values(self.model.iter_variables())
      ret_val = self.getObjectiveValue(new_sol)
    # Clear the constraints
    self.model.remove_constraints(docplex_constraints)

    return new_sol, ret_val


  def preComputeConstraints(self):
    constraint_map = dict()
    for i in range(self.numTests):
      constraint_map["x_" + str(i)] = [None]*2
      c = self.model.get_var_by_name("x_" + str(i)) <= 0
      constraint_map["x_" + str(i)][0] = c
      c = self.model.get_var_by_name("x_" + str(i)) >= 1
      constraint_map["x_" + str(i)][1] = c
    return constraint_map
  
  def branch_and_bound(self):
    upper_bound = math.inf
    lower_bound = self.objVal
    candidate_solutions = []
    candidate_solutions.append((self.solution, self.model, self.objVal))
    best_solution = []
    while candidate_solutions:
      current_solution, current_model, cur_val = candidate_solutions.pop()
      if cur_val > upper_bound:
        continue
      branch_idx = self.greatest_fractional_idx(current_solution)
      if branch_idx == -1:
        new_val = self.getObjectiveValue(current_solution)
        print("integer solution found:", new_val)
        if new_val < upper_bound:
          upper_bound = new_val
          best_solution = current_solution
        continue
      else:
        new_model = current_model.copy()
        new_model.add_constraint(new_model.get_var_by_name("x_" + str(branch_idx)) >= 1)
        new_model.solve()
        new_solution = []
        if new_model.solution != None:
          for var in new_model.iter_variables():
            new_solution.append(new_model.solution.get_value(var))
          new_val = self.getObjectiveValue(new_solution)
          if new_val < upper_bound:
            candidate_solutions.append((new_solution, new_model, new_val))


        new_model = current_model.copy()
        new_model.add_constraint(new_model.get_var_by_name("x_" + str(branch_idx)) <= 0)
        new_model.solve()
        new_solution = []
        if new_model.solution != None:
          for var in new_model.iter_variables():
            new_solution.append(new_model.solution.get_value(var))
          new_val = self.getObjectiveValue(new_solution)
          if new_val < upper_bound:
            candidate_solutions.append((new_solution, new_model, new_val))

    return upper_bound, best_solution


  
  def branch_and_bound_dfs(self):
    upper_bound = math.inf
    best_solution = []

    def dfs(current_solution, constraints, cur_val):
      nonlocal upper_bound, best_solution

      if cur_val >= upper_bound:
        return  # Prune branch

      branch_idx = self.greatest_fractional_idx(current_solution)
      if branch_idx == -1:
        print("integer solution found:", cur_val)
        if cur_val < upper_bound:
          upper_bound = cur_val
          best_solution = current_solution
        return  # Reached leaf node

      # Explore branches (setting x_i to 1 and 0)
      for branch_value in [1, 0]:
        new_constraints = constraints.copy()
        new_constraints[branch_idx] = branch_value
        new_solution, new_obj_val = self.apply_constraints(new_constraints)
        if new_obj_val < upper_bound:
          dfs(new_solution, new_constraints, new_obj_val)

    # Start the DFS from the initial solution
    initial_solution = self.solution
    initial_obj_val = self.objVal
    dfs(initial_solution, dict(), initial_obj_val)

    return upper_bound, best_solution

  def branch_and_bound_best(self):
    upper_bound = math.inf
    best_solution = []
    # Use a priority queue (min-heap)
    priority_queue = [(self.objVal, self.solution, dict())]  # (priority, solution, constraints)
    heapq.heapify(priority_queue)

    while priority_queue:
      new_val, current_solution, constraints = heapq.heappop(priority_queue)
      if new_val > upper_bound:
        continue
      branch_idx = self.greatest_fractional_idx(current_solution)
      if branch_idx == -1:
        print("integer solution found:", new_val)
        if new_val < upper_bound:
          upper_bound = new_val
          best_solution = current_solution
      else:
        new_constraints = constraints.copy()
        new_constraints[branch_idx] = 1
        new_solution, new_obj_val = self.apply_constraints(new_constraints)
        upper_bound = min(upper_bound, self.getRoundUpObjectiveValue(new_solution))
        if new_obj_val <= upper_bound:
          heapq.heappush(priority_queue, (new_obj_val, new_solution, new_constraints))

        new_constraints = constraints.copy()
        new_constraints[branch_idx] = 0
        new_solution, new_obj_val = self.apply_constraints(new_constraints)
        upper_bound = min(upper_bound, self.getRoundUpObjectiveValue(new_solution))
        if new_obj_val <= upper_bound:
          heapq.heappush(priority_queue, (new_obj_val, new_solution, new_constraints))
        
    return upper_bound, best_solution


  def branch_idx(self, cur_sol):
    for i in range(self.numTests):
      if cur_sol[i] > 0 and cur_sol[i] < 1.0:
        return i
    return -1


  def greatest_fractional_idx(self, arr):
    max_fractional = 0
    max_idx = -1
    for i, num in enumerate(arr):
      fractional_part = abs(num - int(num))  # Get the absolute value of the fractional part
      if fractional_part > max_fractional:
        max_fractional = fractional_part
        max_idx = i
    return max_idx

  def distance_to_half(self, arr):
    max_fractional = 0
    max_idx = -1
    for i, num in enumerate(arr):
      fractional_part = abs(num - 0.5)  # Get the absolute value of the fractional part
      if fractional_part > max_fractional:
        max_fractional = fractional_part
        max_idx = i
    return max_idx


  def getObjectiveValue(self, cur_sol):
    return np.sum([cur_sol[i]*self.costOfTest[i] for i in range(self.numTests)])

  def getRoundUpObjectiveValue(self, cur_sol):
    return np.sum([(1.0 if cur_sol[i] > 0 else 0) *self.costOfTest[i] for i in range(self.numTests)])

  def toString(self):
    out = ""
    out = f"Number of test: {self.numTests}\n"
    out+=f"Number of diseases: {self.numDiseases}\n"
    cst_str = " ".join([str(i) for i in self.costOfTest])
    out+=f"Cost of tests: {cst_str}\n"
    A_str = "\n".join([" ".join([str(j) for j in self.A[i]]) for i in range(0,self.A.shape[0])])
    out+=f"A:\n{A_str}"
    return out