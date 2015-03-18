/* 
 * Author: Damian Polan
 */

package FinalProject.districtserver;

import java.net.InetAddress;

import FinalProject.Ballot;
import FinalProject.communication.Comm;
import FinalProject.masterserver.ElectionResults;
import FinalProject.masterserver.MasterServerInformation;
import FinalProject.persons.Person;
import FinalProject.persons.Voter;

/**
 * Acts as an intermediate between Booth and MasterServer
 * 
 * @author damianpolan
 * 
 */
public class DistrictServer implements Runnable {

	public static void main(String args[]) {
		if (args.length >= 4) {
			DistrictServer district = new DistrictServer(
					Integer.parseInt(args[0]), args[1],
					Integer.parseInt(args[2]), args[3]);
			district.start();
		} else {
			System.out
					.println("Arguments[4]: port, masterAddress, masterPort, uniqueDistrictId");
		}
	}

	// communication portal for this district server
	Comm districtComm;

	// connection information
	String masterAddress;
	int masterPort;
	int port;
	String uniqueDistrictId;

	// election data table. The table contains the profile of each voter and the
	// list of candidates.
	MasterServerInformation masterServerInfo; // TEMPORARY until MasterServer
												// rework

	// Election results. pulled from master server
	ElectionResults results;

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
	 * Starts the server
	 */
	public void start() {
		System.out.println("DistrictServer Started\n");
		try {
			districtComm = new Comm(port);
			districtComm.connectToParent(InetAddress.getByName(masterAddress),
					masterPort);
			Thread.sleep(1000);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// a single thread is needed to listen for all incoming / send outgoing
		this.run();
	}

	@Override
	public void run() {
		try {
			// listen for incoming messages. messages can come from booths or
			// master server
			Object recievedMessage = districtComm.getMessageBlocking();

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
				this.masterServerInfo = (MasterServerInformation) recievedMessage;

			} else if (recievedMessage instanceof ElectionResults) {
				this.results = (ElectionResults) recievedMessage;

			} else if (recievedMessage instanceof Person) { // register the
															// person
				Voter localVoter = this.masterServerInfo
						.getVoter(((Person) recievedMessage).getName());

				if (localVoter != null
						&& localVoter.getDistrictId().equals(uniqueDistrictId)) {
					localVoter.setRegistered(true);
					districtComm.sendMessageReply("true");
				} else
					districtComm.sendMessageReply("false");

			} else if (recievedMessage instanceof Ballot) { // vote with this
															// person
				Ballot voteBallot = (Ballot) recievedMessage;

				// district must be matching to vote AND must be registered
				if (voteBallot.district.equals(uniqueDistrictId)
						&& voteBallot.voter.getRegistered()
						&& voteBallot.voter.getDistrictId().equals(
								uniqueDistrictId)) {

					Voter localVoter = this.masterServerInfo
							.getVoter(voteBallot.voter.getName());

					// make the vote
					localVoter.setCandidate(voteBallot.candidate);
					localVoter.setVoted(true);
					districtComm.sendMessageReply("true");

					// TEMPORARY - until structure rework with Jon.
					districtComm.sendMessageParent(masterServerInfo);

				} else
					districtComm.sendMessageReply("false");

			} else if (recievedMessage instanceof String) {
				if (recievedMessage.equals("status")) {
					// send back the ElectionResults to booth
					districtComm.sendMessageReply(results);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}