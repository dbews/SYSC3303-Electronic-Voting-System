/* 
 * Author: Damian Polan
 */

package FinalProject.districtserver;

import java.io.*;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;

import FinalProject.electionobjects.Ballot;
import FinalProject.resultdata.ResultItem;
import FinalProject.resultdata.BoothElectionResults;
import FinalProject.electionobjects.Credential;
import FinalProject.communication.Comm;
import FinalProject.masterserver.MasterServerInformation;
import FinalProject.persons.Candidate;
import FinalProject.persons.Person;
import FinalProject.persons.Voter;

/**
 * Acts as an intermediate between Booth and MasterServer
 * 
 * @author damianpolan
 * 
 */
public class DistrictServer implements Runnable {

	//maintains the shutting down state
	private boolean down;

	/**
	 * Runs the district server with the given params
	 * @param args
	 */
	public static void main(String args[]) {
		if (args.length >= 4) {
			DistrictServer district = new DistrictServer(
					Integer.parseInt(args[0]), args[1],
					Integer.parseInt(args[2]), args[3]);
			if (args.length >= 6)
				district.fakeData(args[4], args[5]);
			district.start();
		} else {
			System.out
					.println("Arguments[4]: port, masterAddress, masterPort, uniqueDistrictId, (optional: candidatePath, votersPath)");
		}
	}

	// communication portal for this district server
	private Comm districtComm;

	// connection information
	private String masterAddress;
	private int masterPort;
	private int port;
	private String uniqueDistrictId; //each district has a unique identifier

	// election data table. The table contains the profile of each voter and the
	// list of candidates.
	private MasterServerInformation masterServerInfo; // TEMPORARY until MasterServer
												// rework

	// Election results. pulled from master server
	// ElectionResults results;
	private BoothElectionResults electionResults;


	//data is faked when set to true. Only for solo testing purposes
	private boolean fake = false;

	/**
	 * Constructor
	 * 
	 * @param port
	 * @param masterAddress
	 * @param masterPort
	 * @param uniqueDistrictId
	 */
	public DistrictServer(int port, String masterAddress, int masterPort,
			String uniqueDistrictId) {
		this.port = port;
		this.masterAddress = masterAddress;
		this.masterPort = masterPort;
		this.uniqueDistrictId = uniqueDistrictId;
	}

	/**
	 * Fakes the given master server information
	 * @param candidatePath
	 * @param voterPath
	 */
	private void fakeData(String candidatePath, String voterPath) {

		// from MasterServer.java

		MasterServerInformation lists = new MasterServerInformation();
		File votersFile = new File(voterPath);
		File candidatesFile = new File(candidatePath);

		try {
			FileInputStream fis1 = new FileInputStream(votersFile);

			// Construct BufferedReader from InputStreamReader
			BufferedReader br1 = new BufferedReader(new InputStreamReader(fis1));
			String line = null;
			String voter = null;
			int district;
			while ((line = br1.readLine()) != null) {
				district = Integer.valueOf(line);
				voter = br1.readLine();
				lists.addVoter(new Voter(voter, "", district + ""));
			}
			br1.close();
		} catch (Exception e) {
			System.out.println("Error reading voters file.");
			e.printStackTrace();
			System.exit(-1);
		}

		String candidate = null;
		String party = null;
		try {
			FileInputStream fis2 = new FileInputStream(candidatesFile);
			// Construct BufferedReader from InputStreamReader
			BufferedReader br2 = new BufferedReader(new InputStreamReader(fis2));
			while ((party = br2.readLine()) != null) {
				candidate = br2.readLine();
				lists.addCandidate(new Candidate(candidate, party));
			}

			br2.close();
		} catch (Exception e) {
			System.out.println("Error reading Candidates file.");
			e.printStackTrace();
			System.exit(-1);
		}

		masterServerInfo = lists;

		Enumeration<Candidate> c = masterServerInfo.getCandidates().elements();
		int i = 0;
		while (c.hasMoreElements()) {
			c.nextElement();
			i++;
		}
		//
		ResultItem[] r = new ResultItem[i];

		c = masterServerInfo.getCandidates().elements();
		i = 0;
		while (c.hasMoreElements()) {
			r[i++] = new ResultItem(c.nextElement(), 0);

		}
		//
		electionResults = new BoothElectionResults(r, 0);

		fake = true;
	}

	/**
	 * Starts the server on the set port
	 */
	private void start() {
		// System.out.println("DistrictServer Started");
		try {
			districtComm = new Comm(port);
			districtComm.connectToParent(InetAddress.getByName(masterAddress),
					masterPort);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// a single thread is needed to listen for all incoming / send outgoing
		this.run();
	}

	/**
	 * Safely shuts the closes this district server
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void shutdown() throws IOException, InterruptedException {
		if (down)
			return;

		// System.out.println("District Server " + uniqueDistrictId + ": " +
		// "closed");
		districtComm.sendMessageClient("end");

		Thread.sleep(5000);

		districtComm.shutdown();
		down = true;
	}

	
	/**
	 * Main thread function.
	 * Maintains all connections to clients and master server. 
	 */
	@Override
	public void run() {
		boolean continues = true;
		while (continues) {
			try {
				if (districtComm == null)
					break;

				// listen for incoming messages. messages can come from booths
				// or
				// master server
				Object recievedMessage = districtComm.getMessageBlocking();
				// System.out.println(recievedMessage);
				// from master server:
				// Receive MasterServerInformation containing all candidate and
				// voters
				// periodically receive ElectionResults
				// periodically send MasterServerInformation to master server

				// from client:
				// "status" -> ElectionResults
				// Person -> "true" or "false" registration confirmed or not
				// Ballot -> "true" or "false" vote valid (must be registered)

				if (recievedMessage instanceof MasterServerInformation) {
					if (!fake) {
						this.masterServerInfo = (MasterServerInformation) recievedMessage;

						Enumeration<Voter> it = ((MasterServerInformation) recievedMessage)
								.getVoters().elements();
						while (it.hasMoreElements()) {
							Voter v = it.nextElement();

							if (!v.getDistrictId().equals(uniqueDistrictId)) {
								masterServerInfo.getVoters()
										.remove(v.getName());
							}

						}

					}
				} else if (recievedMessage instanceof BoothElectionResults) {
					// System.out.println("District: received election results");
					this.electionResults = (BoothElectionResults) recievedMessage;
				} else if (recievedMessage instanceof Person) { // register the
					// person
					// System.out.println("Registering Person");
					String nm = ((Person) recievedMessage).getName();
					if (this.masterServerInfo == null || nm == null) {
						int a = 0;
					}

					Voter localVoter = this.masterServerInfo.getVoter(nm);

					if (localVoter != null
							&& localVoter.getDistrictId().equals(
									uniqueDistrictId)
							&& !localVoter.getRegistered()) {
						localVoter.setRegistered(true);
						districtComm.sendMessageReply("true");
						// System.out.println("District Server " +
						// uniqueDistrictId + ": " + "Registered Successful");
					} else {
						districtComm.sendMessageReply("false");
						// System.out.println("Not Registered");
					}

				} else if (recievedMessage instanceof Ballot) { // vote with
																// this
																// System.out.println("District Server "
																// +
																// uniqueDistrictId
					// + ": " + "Attempting to vote");
					// person
					Ballot voteBallot = (Ballot) recievedMessage;

					// district must be matching to vote AND must be registered
					Voter v = voteBallot.getVoter();

					if (masterServerInfo.getVoter(v.getName()).getRegistered()) {
						Voter localVoter = this.masterServerInfo
								.getVoter(voteBallot.getVoter().getName());

						// make the vote
						localVoter.setCandidate(voteBallot.getCandidate());
						localVoter.setVoted(true);

						for (int i = 0; i < electionResults.results.length; i++)
							if (electionResults.results[i].candidate
									.getName()
									.equals(voteBallot.getCandidate().getName())) {
								electionResults.results[i].count++;
							}

						electionResults.totalVotes++;
						electionResults.generated = new Date();
						// System.out.println("" + electionResults.toString());

						districtComm.sendMessageReply("true");
						// System.out.println("District Server "
						// + uniqueDistrictId + ": " + "Vote Successful");

						// save results to file
						PrintWriter writer = new PrintWriter(
								"election-results.txt", "UTF-8");
						writer.println(electionResults.toString());
						writer.close();

						// TEMPORARY - until structure rework with Jon.
						if (!fake)
							districtComm.sendMessageParent(localVoter);

					} else {
						districtComm.sendMessageReply("false");
						// System.out.println("District Server " +
						// uniqueDistrictId + ": " + "Voting failed");
					}

					// if (v.getName().equals("Ellena Jeanbaptiste")) {
					// try {
					// districtComm.shutdown();
					// } catch (InterruptedException e) {
					// System.exit(0);
					// }
					// System.exit(0);
					// }

				} else if (recievedMessage instanceof Credential) {
					Credential creds = (Credential) recievedMessage;
					// System.out.println("District Server " + uniqueDistrictId
					// + ": " + "Checking Credentials");

					// check user match
					if (masterServerInfo.getVoter(creds.getUser()).getUser()
							.equals(creds.getUser())
							&& masterServerInfo.getVoter(creds.getUser())
									.getPin().equals(creds.getPin())) {

						districtComm.sendMessageReply("true");
						// System.out.println("District Server " +
						// uniqueDistrictId + ": " + "Credentials valid");
					} else {
						districtComm.sendMessageReply("false");
						// System.out.println("District Server " +
						// uniqueDistrictId + ": " + "Credentials failed");
					}

				} else if (recievedMessage instanceof String) {
					if (recievedMessage.equals("status")) {
						// send back the ElectionResults to booth
						// System.out.println("District sending status");
						districtComm.sendMessageReply(electionResults);
					} else if (recievedMessage.equals("candidates")) {

						// System.out.println("District Server " +
						// uniqueDistrictId + ": " + "Sending Candidates");
						Candidate[] c = new Candidate[masterServerInfo
								.getCandidates().size()];

						int i = 0;

						Enumeration<String> str = masterServerInfo
								.getCandidates().keys();
						String s;
						while (str.hasMoreElements()) {
							s = str.nextElement();
							c[i] = masterServerInfo.getCandidates().get(s);
							i++;
						}
						districtComm.sendMessageReply(c);
					} else if (recievedMessage.equals("end")) {
						shutdown();
						continues = false;
					} else if (recievedMessage.equals("district")) {
						districtComm.sendMessageReply(uniqueDistrictId);
					} else {
						// System.out.println("Unhandled string " +
						// recievedMessage);
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		try {
			shutdown();
		} catch (Exception e) {
			// do nothing if errors
		}
	}

}
