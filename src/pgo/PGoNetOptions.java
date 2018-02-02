package pgo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Vector;

// Wraps options related to networking in the generated Go code.
// Networking related options are defined in the JSON configuration
// file and specify details about endpoints (e.g. IP addresses
// and ports). See the +config-sample.json+ file included in this
// repository for an example of how to define these properties.

// Networking options involve a number of concepts: shared state
// management, the separation of different processes in the network, and
// how they are connected together. Each of these components are handled
// by separate classes (see documentation for the inner classes below).
//
// If there are semantic errors in the configuration of any of these
// aspects, the associated class throws an exception. That is caught
// by the +PGoOptions+ class (when processing user input) which is then able
// to display an appropriate error message to the user.
public class PGoNetOptions {

	// This class encapsulates state management options. Since processes now run in
	// separate hosts, shared state needs to be mapped either to a centralized source
	// or distributed among a number of nodes in the network.
	//
	// This class ensures that the options provided in the configuration file make
	// sense, i.e., whether they use a known/supported state management strategy.
	public class StateOptions {
		private static final String STATE_CENTRALIZED = "centralized";
		private final String[] STATE_STRATEGIES = {
				STATE_CENTRALIZED
		};

		private static final String DEFAULT_STATE_STRATEGY = STATE_CENTRALIZED;
		private static final int DEFAULT_TIMEOUT = 3;

		public String strategy;
		public Vector<String> endpoints;
		public int timeout;

		public StateOptions(JSONObject config) throws PGoOptionException {
			int i;
			this.endpoints = new Vector<>();

			if (config.has("strategy")) {
				this.strategy = config.getString("strategy");
			} else {
				this.strategy = DEFAULT_STATE_STRATEGY;
			}

			JSONArray endpoints = config.getJSONArray("endpoints");
			for (i = 0; i < endpoints.length(); i++) {
				this.endpoints.add(endpoints.getString(i));
			}

			if (config.has("timeout")) {
				this.timeout = config.getInt("timeout");
			} else {
				this.timeout = DEFAULT_TIMEOUT;
			}

			validate();
		}

		private void validate() throws PGoOptionException {
			if (!Arrays.asList(STATE_STRATEGIES).contains(this.strategy)) {
				throw new PGoOptionException("Invalid state strategy: " + this.strategy);
			}
		}
	}

	// fields to be extracted from the JSON configuration file
	public static final String NETWORKING_FIELD = "networking";
	public static final String STATE_FIELD = "state";

	// allows the developer to easily turn off networking by setting this parameter to +false+
	private boolean enabled;

	private StateOptions stateOptions;

	// This constructor expects a +JSONObject+ as parameter - this should be the data structure
	// representing the entire configuration file. Separate parts of the configuration file
	// are then passed to specific components (see inner classes above), each of which has
	// the responsibility to verify whether the configuration is valid.
	public PGoNetOptions(JSONObject config) throws PGoOptionException {
		if (!config.has(NETWORKING_FIELD)) {
			enabled = false;
			return;
		}

		try {
			JSONObject netConfig = config.getJSONObject(NETWORKING_FIELD);
			if (!netConfig.getBoolean("enabled")) {
				enabled = false;
				return;
			}

			JSONObject stateConfig = netConfig.getJSONObject(STATE_FIELD);
			enabled = true;
			stateOptions = new StateOptions(stateConfig);

		} catch (JSONException e) {
			throw new PGoOptionException("Configuration is invalid: " + e.getMessage());
		}
	}

	public boolean isEnabled() {
		return this.enabled;
	}
	public StateOptions getStateOptions() { return this.stateOptions; }
}
