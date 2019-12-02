import org.junit.Before;
import org.junit.Test;

public class ParserTest extends FJParserTester {
	@Before
	public void setUp() throws Exception {
		tester = new FJParserTester();
	}

	// Kann nicht mehr funktionieren, weil ich die bin�ren Operatoren +, -, * und / rausgeschmissen habe,
	// um mich bei der Implementierung des Typsystems besser am Paper anlehnen zu k�nnen.
	//@Test
	public void testComplex() throws Exception {
		parseAndCheckFile("Complex.fj");
	}

	@Test
	public void testTestCase() throws Exception {
		parseAndCheckFile("TestCase.fj");
	}
}
