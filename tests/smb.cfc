component extends="org.lucee.cfml.test.LuceeTestCase" labels="smb" {

	// Initialize smbPath at component level so skip functions can access it
	variables.smbPort = server.system.environment.SMB_PORT ?: "445";
	variables.smbPath = "smb://smbuser:smbpass@localhost:#variables.smbPort#/public/";

	function beforeAll() {
		// dperson/samba defaults: user=smbuser, pass=smbpass, share=public at /share
		variables.testDir = variables.smbPath & "smb-test-" & createUUID() & "/";
		// Cleanup at start - leave artifacts for inspection after test
		_cleanupTestDir();
	}

	function afterAll() {
		// Don't cleanup - leave for inspection per CLAUDE.md
	}

	private function _cleanupTestDir() {
		try {
			if ( directoryExists( variables.testDir ) ) {
				directoryDelete( variables.testDir, true );
			}
		}
		catch ( any e ) {
			// ignore cleanup errors
		}
	}

	function test_smb_scheme_registered() {
		var res = getResource( "smb://localhost/" );
		expect( res.getResourceProvider().getScheme() ).toBe( "smb" );
	}

	function test_directory_create_exists_delete() skip="skipSmbTests" {
		_cleanupTestDir();
		// Create directory
		directoryCreate( variables.testDir );
		expect( directoryExists( variables.testDir ) ).toBeTrue();

		// Delete directory
		directoryDelete( variables.testDir );
		expect( directoryExists( variables.testDir ) ).toBeFalse();
	}

	function test_file_write_read_delete() skip="skipSmbTests" {
		_cleanupTestDir();
		var testFile = variables.testDir & "test.txt";
		var content = "Hello SMB! " & now();

		// Create parent dir
		directoryCreate( variables.testDir );

		// Write file
		fileWrite( testFile, content );
		expect( fileExists( testFile ) ).toBeTrue();

		// Read file
		var readContent = fileRead( testFile );
		expect( readContent ).toBe( content );

		// Delete file
		fileDelete( testFile );
		expect( fileExists( testFile ) ).toBeFalse();
	}

	function test_directory_list() skip="skipSmbTests" {
		_cleanupTestDir();
		// Create test directory with files
		directoryCreate( variables.testDir );
		fileWrite( variables.testDir & "file1.txt", "content1" );
		fileWrite( variables.testDir & "file2.txt", "content2" );

		// List directory
		var files = directoryList( variables.testDir, false, "name" );
		expect( files ).toHaveLength( 2 );
		expect( files ).toInclude( "file1.txt" );
		expect( files ).toInclude( "file2.txt" );
	}

	function test_file_copy() skip="skipSmbTests" {
		_cleanupTestDir();
		var srcFile = variables.testDir & "source.txt";
		var destFile = variables.testDir & "dest.txt";

		directoryCreate( variables.testDir );
		fileWrite( srcFile, "copy test" );

		fileCopy( srcFile, destFile );

		expect( fileExists( destFile ) ).toBeTrue();
		expect( fileRead( destFile ) ).toBe( "copy test" );
	}

	function test_file_move() skip="skipSmbTests" {
		_cleanupTestDir();
		var srcFile = variables.testDir & "move-src.txt";
		var destFile = variables.testDir & "move-dest.txt";

		directoryCreate( variables.testDir );
		fileWrite( srcFile, "move test" );

		fileMove( srcFile, destFile );

		expect( fileExists( srcFile ) ).toBeFalse();
		expect( fileExists( destFile ) ).toBeTrue();
		expect( fileRead( destFile ) ).toBe( "move test" );
	}

	boolean function skipSmbTests() {
		return !_smbAvailable();
	}

	private boolean function _smbAvailable() {
		try {
			return directoryExists( variables.smbPath );
		}
		catch ( any e ) {
			return false;
		}
	}

	private function getResource( required string path ) {
		return createObject( "java", "lucee.loader.engine.CFMLEngineFactory" )
			.getInstance()
			.getResourceUtil()
			.toResourceExisting( getPageContext(), arguments.path );
	}
}
