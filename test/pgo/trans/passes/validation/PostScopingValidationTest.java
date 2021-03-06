package pgo.trans.passes.validation;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import pgo.errors.Issue;
import pgo.errors.TopLevelIssueContext;
import pgo.model.mpcal.ModularPlusCalBlock;
import pgo.model.pcal.PlusCalFairness;
import pgo.model.pcal.PlusCalVariableDeclaration;
import pgo.model.tla.TLAIdentifier;
import pgo.model.tla.TLAModule;
import pgo.modules.TLAModuleLoader;
import pgo.trans.intermediate.DefinitionRegistry;
import pgo.trans.passes.scope.ScopingPass;
import pgo.trans.passes.validation.InconsistentInstantiationIssue;
import pgo.trans.passes.validation.InvalidArchetypeResourceUsageIssue;
import pgo.trans.passes.validation.ValidationPass;
import pgo.util.SourceLocation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static pgo.model.mpcal.ModularPlusCalBuilder.*;
import static pgo.model.pcal.PlusCalBuilder.*;
import static pgo.model.tla.TLABuilder.*;

@RunWith(Parameterized.class)
public class PostScopingValidationTest {
	@Parameterized.Parameters
	public static List<Object[]> data() {
		PlusCalVariableDeclaration invalidAssignmentNonMapped =
				pcalVarDecl("nonMapped", true, false, PLUSCAL_DEFAULT_INIT_VALUE);
		PlusCalVariableDeclaration invalidAssignmentVarMapped =
				pcalVarDecl("varMapped", true, false, PLUSCAL_DEFAULT_INIT_VALUE);
		PlusCalVariableDeclaration invalidAssignmentFnMapped =
				pcalVarDecl("fnMapped", true, false, PLUSCAL_DEFAULT_INIT_VALUE);
		return Arrays.asList(new Object[][] {
				// --mpcal InconsistentInstantiations {
				//   mapping macro MyMapping {
				//     read {
				//     }
				//     write {
				//     }
				//   }
				//   mapping macro OtherMapping {
				//     read {
				//     }
				//     write {
				//     }
				//   }
				//   mapping macro SomeMapping {
				//     read {
				//     }
				//     write {
				//     }
				//   }
				//   archetype A1(ref a1) {
				//     l1:
				//        print 1;
				//   }
				//   archetype A2(ref a2) {
				//     l2:
				//        print 2;
				//   }
				//   variables network = <<>>;
				//   fair process (P1 = 1) == instance A1(ref network)
				//       mapping network[_] via MyMapping;
				//   fair process (Other = 42) == instance A2(ref network);
				//   fair process (P2 = 2) == instance A1(ref network)     \* conflicts with P1
				//       mapping network via OtherMapping;
				//   fair process (P3 = 3) == instance A1(network);        \* conflicts with P1
				//   fair process (Other2 = 24) == instance A2(network)    \* conflicts with Other
				//       mapping network[_] via SomeMapping;
				// }
				{
						mpcal(
								"InconsistentInstantiations",
								Collections.emptyList(),
								Collections.emptyList(),
								Collections.emptyList(),
								Arrays.asList(
										mappingMacro("MyMapping", Collections.emptyList(), Collections.emptyList()),
										mappingMacro("OtherMapping", Collections.emptyList(), Collections.emptyList()),
										mappingMacro("SomeMapping", Collections.emptyList(), Collections.emptyList())),
								Arrays.asList(
										archetype("A1",
												Collections.singletonList(pcalVarDecl("a1", true, false, PLUSCAL_DEFAULT_INIT_VALUE)),
												Collections.emptyList(),
												Collections.singletonList(
														labeled(label("l1"),
																printS(num(1))))),
										archetype("A2",
												Collections.singletonList(pcalVarDecl("a2", true, false, PLUSCAL_DEFAULT_INIT_VALUE)),
												Collections.emptyList(),
												Collections.singletonList(
														labeled(label("l2"),
																printS(num(2)))))),
								Collections.singletonList(pcalVarDecl("network", false, false, tuple())),
								Arrays.asList(
										instance(
												pcalVarDecl("P1", false, false, num(1)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(ref("network")),
												Collections.singletonList(
														mapping("network", true, "MyMapping")
												)
										),

										instance(
												pcalVarDecl("Other", false, false, num(42)),
												PlusCalFairness.WEAK_FAIR,
												"A2",
												Collections.singletonList(ref("network")),
												Collections.emptyList()
										),

										instance(
												pcalVarDecl("P2", false, false, num(2)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(ref("network")),
												Collections.singletonList(
														mapping("network", false, "OtherMapping")
												)
										),

										instance(
												pcalVarDecl("P3", false, false, num(3)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(idexp("network")),
												Collections.emptyList()
										),

										instance(
												pcalVarDecl("Other2", false, false, num(24)),
												PlusCalFairness.WEAK_FAIR,
												"A2",
												Collections.singletonList(idexp("network")),
												Collections.singletonList(
														mapping("network", true, "SomeMapping")
												)
										)
								)
						),
						Arrays.asList(
								new InconsistentInstantiationIssue(
										instance(
												pcalVarDecl("P2", false, false, num(2)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(ref("network")),
												Collections.singletonList(
														mapping("network", false, "OtherMapping")
												)
										),
										instance(
												pcalVarDecl("P1", false, false, num(1)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(ref("network")),
												Collections.singletonList(
														mapping("network", true, "MyMapping")
												)
										)
								),

								new InconsistentInstantiationIssue(
										instance(
												pcalVarDecl("P3", false, false, num(3)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(idexp("network")),
												Collections.emptyList()
										),

										instance(
												pcalVarDecl("P1", false, false, num(1)),
												PlusCalFairness.WEAK_FAIR,
												"A1",
												Collections.singletonList(ref("network")),
												Collections.singletonList(
														mapping("network", true, "MyMapping")
												)
										)
								),

								new InconsistentInstantiationIssue(
										instance(
												pcalVarDecl("Other2", false, false, num(24)),
												PlusCalFairness.WEAK_FAIR,
												"A2",
												Collections.singletonList(idexp("network")),
												Collections.singletonList(
														mapping("network", true, "SomeMapping")
												)
										),

										instance(
												pcalVarDecl("Other", false, false, num(42)),
												PlusCalFairness.WEAK_FAIR,
												"A2",
												Collections.singletonList(ref("network")),
												Collections.emptyList()
										)
								)
						)
				},

				// --mpcal MultipleMapping {
				//   mapping macro Macro {
				//     read {
				//     }
				//     write {
				//     }
				//   }
				//   archetype A(ref a) {
				//     l1:
				//       print 1;
				//   }
				//   variables global = <<>>;
				//   process (P = 1) == instance A(ref global)
				//       mapping global[_] via Macro;
				//   process (Q = 2) == instance A(ref global)
				//       mapping @1 via Macro;
				// }
				{
						mpcal(
								"MultipleMapping",
								Collections.emptyList(),
								Collections.emptyList(),
								Collections.emptyList(),
								Collections.singletonList(
										mappingMacro("Macro", Collections.emptyList(), Collections.emptyList())),
								Collections.singletonList(
										archetype(
												"A",
												Collections.singletonList(pcalVarDecl("a", true, false, PLUSCAL_DEFAULT_INIT_VALUE)),
												Collections.emptyList(),
												Collections.singletonList(
														labeled(label("l1"), printS(num(1)))))),
								Collections.singletonList(pcalVarDecl("global", false, false, tuple())),
								Arrays.asList(
										instance(
												pcalVarDecl("P", false, false, num(1)),
												PlusCalFairness.UNFAIR,
												"A",
												Collections.singletonList(ref("global")),
												Collections.singletonList(
														mapping("global", true, "Macro"))),
										instance(
												pcalVarDecl("Q", false, false, num(2)),
												PlusCalFairness.UNFAIR,
												"A",
												Collections.singletonList(ref("global")),
												Collections.singletonList(
														mapping(1, false, "Macro"))))),
						Collections.singletonList(
								new InconsistentInstantiationIssue(
										instance(
												pcalVarDecl("Q", false, false, num(2)),
												PlusCalFairness.UNFAIR,
												"A",
												Collections.singletonList(ref("global")),
												Collections.singletonList(
														mapping(1, false, "Macro"))),
										instance(
												pcalVarDecl("P", false, false, num(1)),
												PlusCalFairness.UNFAIR,
												"A",
												Collections.singletonList(ref("global")),
												Collections.singletonList(
														mapping("global", true, "Macro")))))
				},

				// --mpcal InvalidAssignments {
				//     mapping macro SomeMacro {
				//       read {
				//       }
				//       write {
				//       }
				//     }
				//
				//     mapping macro OtherMacro {
				//       read {
				//       }
				//       write {
				//       }
				//     }
				//
				//     archetype MyArchetype(ref nonMapped, ref varMapped, ref fnMapped) {
				//         l1:
				//           while (TRUE) {
				//               nonMapped := 10;                  \* valid
				//               someLocal := fnMapped[nonMapped]; \* valid
				//           }
				//
				//         l2:
				//           either { varMapped[10] := 0; } \* invalid
				//           or     { varMapped := 10;    } \* valid
				//
				//         l3:
				//           if (varMapped[10] = 10) {       \* invalid
				//               someLocal := varMapped[10]; \* invalid
				//           } else {
				//               nonMapped[30] := 20;        \* valid
				//               fnMapped := 12;             \* invalid
				//           }
				//     }
				//
				//     variables n = 0, v = 0, f = <<0>>;
				//
				//     process (P1 = 42) == instance MyArchetype(ref n, ref v, ref f)
				//         mapping v via SomeMacro
				//         mapping f[_] via OtherMacro;
				// }
				{
						mpcal(
								"InvalidAssignments",
								Collections.emptyList(),
								Collections.emptyList(),
								Collections.emptyList(),
								Arrays.asList(
										mappingMacro("SomeMacro", Collections.emptyList(), Collections.emptyList()),
										mappingMacro("OtherMacro", Collections.emptyList(), Collections.emptyList())),
								Collections.singletonList(
										archetype(
												"MyArchetype",
												Arrays.asList(
														invalidAssignmentNonMapped,
														invalidAssignmentVarMapped,
														invalidAssignmentFnMapped),
												Collections.singletonList(
														pcalVarDecl("someLocal", false, false, PLUSCAL_DEFAULT_INIT_VALUE)),
												Arrays.asList(
														labeled(
																label("l1"),
																whileS(
																		bool(true),
																		Arrays.asList(
																				assign(idexp("nonMapped"), num(10)),
																				assign(idexp("someLocal"), fncall(idexp("fnMapped"), idexp("nonMapped")))
																		)
																)
														),
														labeled(
																label("l2"),
																either(Arrays.asList(
																		Collections.singletonList(
																				assign(fncall(idexp("varMapped"), num(10)), num(0))
																		),
																		Collections.singletonList(
																				assign(idexp("varMapped"), num(10))
																		)
																))
														),
														labeled(
																label("l3"),
																ifS(
																		binop("=", fncall(idexp("varMapped"), num(10)), num(10)),
																		Collections.singletonList(
																				assign(idexp("someLocal"), fncall(idexp("varMapped"), num(10)))
																		),
																		Arrays.asList(
																				assign(fncall(idexp("nonMapped"), num(30)), num(20)),
																				assign(idexp("fnMapped"), num(12))
																		)
																)
														)
												)
										)
								),
								Arrays.asList(
										pcalVarDecl("n", false, false, num(0)),
										pcalVarDecl("v", false, false, num(0)),
										pcalVarDecl("f", false, false, tuple(num(0)))),
								Collections.singletonList(
										instance(
												pcalVarDecl("P1", false, false, num(1)),
												PlusCalFairness.WEAK_FAIR,
												"MyArchetype",
												Arrays.asList(
														ref("n"),
														ref("v"),
														ref("f")),
												Arrays.asList(
														mapping("v", false, "SomeMacro"),
														mapping("f", true, "OtherMacro"))
										)
								)
						),
						Arrays.asList(
								new InvalidArchetypeResourceUsageIssue(
										assign(fncall(idexp("varMapped"), num(10)), num(0)),
										invalidAssignmentVarMapped.getUID(), false),
								new InvalidArchetypeResourceUsageIssue(
										ifS(
												binop("=", fncall(idexp("varMapped"), num(10)), num(10)),
												Collections.singletonList(
														assign(idexp("someLocal"), fncall(idexp("varMapped"), num(10)))
												),
												Arrays.asList(
														assign(fncall(idexp("nonMapped"), num(30)), num(20)),
														assign(idexp("fnMapped"), num(12))
												)
										),
										invalidAssignmentVarMapped.getUID(), false),
								new InvalidArchetypeResourceUsageIssue(
										assign(idexp("someLocal"), fncall(idexp("varMapped"), num(10))),
										invalidAssignmentVarMapped.getUID(), false),
								new InvalidArchetypeResourceUsageIssue(
										assign(fncall(idexp("nonMapped"), num(30)), num(20)),
										invalidAssignmentNonMapped.getUID(), false),
								new InvalidArchetypeResourceUsageIssue(
										assign(idexp("fnMapped"), num(12)),
										invalidAssignmentFnMapped.getUID(), true))
				}
		});
	}

	private final ModularPlusCalBlock spec;
	private final List<Issue> issues;

	public PostScopingValidationTest(ModularPlusCalBlock spec, List<Issue> issues) {
		this.spec = spec;
		this.issues = issues;
	}

	@Test
	public void test() {
		TopLevelIssueContext ctx = new TopLevelIssueContext();

		ValidationPass.perform(ctx, spec);
		assertFalse(ctx.hasErrors());

		TLAModuleLoader loader = new TLAModuleLoader(Collections.emptyList());
		DefinitionRegistry registry = ScopingPass.perform(
				ctx,
				true,
				loader,
				new HashMap<>(),
				new TLAModule(
						SourceLocation.unknown(),
						new TLAIdentifier(SourceLocation.unknown(), "Test"),
						Collections.emptyList(),
						Collections.emptyList(),
						Collections.emptyList(),
						Collections.emptyList()),
				spec);
		assertFalse(ctx.hasErrors());

		ValidationPass.performPostScoping(ctx, registry, spec);
		assertEquals(issues, ctx.getIssues());
	}
}
