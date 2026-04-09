from z3 import *

solver = Solver()

# Using pure Integer algebra instead of BitVecs prevents Z3 from hanging
state = Int('state')
solver.add(state >= 0, state < 2**48)

current = state
multiplier = 0x5DEECE66D
addend = 0xB
targets = [23, 6, 9, 21, 18, 23, 2, 4, 6, 18]

for i, target in enumerate(targets):
    # 1. Advance the LCG state: next_state = (current * multiplier + addend) % 2^48
    next_state = Int(f'state_{i}')
    wrap_around = Int(f'wrap_{i}')
    solver.add(current * multiplier + addend == next_state + wrap_around * (2**48))
    solver.add(next_state >= 0, next_state < 2**48)
    current = next_state
    
    # 2. Shift right by 17 bits (which is exactly integer division by 2^17 or 131072)
    next31 = Int(f'next31_{i}')
    rem17 = Int(f'rem17_{i}')
    solver.add(current == next31 * 131072 + rem17)
    solver.add(rem17 >= 0, rem17 < 131072)
    
    # 3. Apply the modulo 26 alphabet constraint
    k = Int(f'k_{i}')
    solver.add(next31 == k * 26 + target)
    solver.add(k >= 0)

print("Cracking seed...")
if solver.check() == sat:
    m = solver.model()
    initial_state = m[state].as_long()
    
    # Reverse the initial XOR scramble done by Java's Random constructor
    original_seed = initial_state ^ multiplier
    print(f"Success! The cracked seed is: {original_seed}L")
else:
    print("No seed found.")