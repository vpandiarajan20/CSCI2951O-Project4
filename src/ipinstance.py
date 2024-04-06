from dataclasses import dataclass
import numpy  as np
from docplex.mp.model import Model

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
    self.objVal = self.solve()
  
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
    x = self.model.integer_var_list(self.numTests, 0, 1, name="x")
    # x = self.model.continuous_var_list(self.numTests, 0, 1, name="x")

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
    return self.model.solution.get_objective_value()

  def toString(self):
    out = ""
    out = f"Number of test: {self.numTests}\n"
    out+=f"Number of diseases: {self.numDiseases}\n"
    cst_str = " ".join([str(i) for i in self.costOfTest])
    out+=f"Cost of tests: {cst_str}\n"
    A_str = "\n".join([" ".join([str(j) for j in self.A[i]]) for i in range(0,self.A.shape[0])])
    out+=f"A:\n{A_str}"
    return out