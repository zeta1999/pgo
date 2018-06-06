------ MODULE AST -------
EXTENDS TLC
fairness == ""
 
ast == 
[type     |-> "uniprocess", 
 name  |-> "Euclid", 
 decls  |-> <<[var |-> "u", eqOrIn |-> "=", val |-> << "24" >>], 
              [var |-> "v", eqOrIn |-> "\\in", val |-> << "1", "..", "N" >>], 
              [var |-> "v_init", eqOrIn |-> "=", val |-> << "v" >>], 
              [var |-> "pgo_read", eqOrIn |-> "=", val |-> << "[ << var, lab >> \in {"u", "v", "v_init"} \X {"Lbl_1", "Lbl_2"} |-> 0 ]" >>], 
              [var |-> "pgo_write", eqOrIn |-> "=", val |-> << "[ << var, lab >> \in {"u", "v", "v_init"} \X {"Lbl_1", "Lbl_2"} |-> 0 ]" >>]>>,
 defs   |-> <<  >>,
 prcds  |-> <<>>,
 body  |-> <<[label |-> "Lbl_1",
              stmts |-> <<[type    |-> "while", 
                           test    |-> << "u", "#", "0" >>,
                           labDo   |-> <<[label |-> "pgo_Lbl_1",
                                          stmts |-> <<[type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "pgo_read", sub |-> << "[ "u", "Lbl_1" ]" >>],
                                                                   rhs |-> << "pgo_read[ "u", "Lbl_1" ] - 1" >>], 
                                                                  [lhs |-> [var |-> "pgo_read", sub |-> << "[ "v", "Lbl_1" ]" >>],
                                                                   rhs |-> << "pgo_read[ "v", "Lbl_1" ] - 1" >>]>>], 
                                                      [type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "pgo_write", sub |-> << "[ "u", "Lbl_1" ]" >>],
                                                                   rhs |-> << "pgo_write[ "u", "Lbl_1" ] - 1" >>], 
                                                                  [lhs |-> [var |-> "pgo_write", sub |-> << "[ "v", "Lbl_1" ]" >>],
                                                                   rhs |-> << "pgo_write[ "v", "Lbl_1" ] - 1" >>]>>]>>], 
                                         [label |-> "Lbl_2",
                                          stmts |-> <<[type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "u", sub |-> <<  >>],
                                                                   rhs |-> << "u", "-", "v" >>]>>], 
                                                      [type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "pgo_read", sub |-> << "[ "u", "Lbl_2" ]" >>],
                                                                   rhs |-> << "pgo_read[ "u", "Lbl_2" ] + 1" >>], 
                                                                  [lhs |-> [var |-> "pgo_read", sub |-> << "[ "v", "Lbl_2" ]" >>],
                                                                   rhs |-> << "pgo_read[ "v", "Lbl_2" ] + 1" >>]>>], 
                                                      [type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "pgo_write", sub |-> << "[ "u", "Lbl_2" ]" >>],
                                                                   rhs |-> << "pgo_write[ "u", "Lbl_2" ] + 1" >>]>>]>>], 
                                         [label |-> "pgo_Lbl_2",
                                          stmts |-> <<[type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "pgo_read", sub |-> << "[ "u", "Lbl_2" ]" >>],
                                                                   rhs |-> << "pgo_read[ "u", "Lbl_2" ] - 1" >>], 
                                                                  [lhs |-> [var |-> "pgo_read", sub |-> << "[ "v", "Lbl_2" ]" >>],
                                                                   rhs |-> << "pgo_read[ "v", "Lbl_2" ] - 1" >>]>>], 
                                                      [type |-> "assignment",
                                                       ass  |-> <<[lhs |-> [var |-> "pgo_write", sub |-> << "[ "u", "Lbl_2" ]" >>],
                                                                   rhs |-> << "pgo_write[ "u", "Lbl_2" ] - 1" >>]>>]>>]>>,
                           unlabDo |-> <<[type    |-> "if", 
                                          test    |-> << "u", "<", "v" >>,
                                          then |-> <<[type |-> "assignment",
                                                      ass  |-> <<[lhs |-> [var |-> "u", sub |-> <<  >>],
                                                                  rhs |-> << "v" >>], 
                                                                 [lhs |-> [var |-> "v", sub |-> <<  >>],
                                                                  rhs |-> << "u" >>]>>]>>,
                                          else |-> <<>>], 
                                         [type |-> "assignment",
                                          ass  |-> <<[lhs |-> [var |-> "pgo_read", sub |-> << "[ "u", "Lbl_1" ]" >>],
                                                      rhs |-> << "pgo_read[ "u", "Lbl_1" ] + 1" >>], 
                                                     [lhs |-> [var |-> "pgo_read", sub |-> << "[ "v", "Lbl_1" ]" >>],
                                                      rhs |-> << "pgo_read[ "v", "Lbl_1" ] + 1" >>]>>], 
                                         [type |-> "assignment",
                                          ass  |-> <<[lhs |-> [var |-> "pgo_write", sub |-> << "[ "u", "Lbl_1" ]" >>],
                                                      rhs |-> << "pgo_write[ "u", "Lbl_1" ] + 1" >>], 
                                                     [lhs |-> [var |-> "pgo_write", sub |-> << "[ "v", "Lbl_1" ]" >>],
                                                      rhs |-> << "pgo_write[ "v", "Lbl_1" ] + 1" >>]>>]>>], 
                          [type |-> "print", 
                           exp |-> << "<<", "24", ",", "v_init", ",", "\"", "have gcd", "\"", ",", "v", ">>" >>]>>]>>]
==========================
