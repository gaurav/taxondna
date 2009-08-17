#!/usr/bin/perl -w

=head1 NAME

basic.t

=head2 GOALS

Very basic tests for SequenceMatrix

=cut

use Test::More;

plan(tests => 1);

sub exec_sequence_matrix {
    system("/usr/bin/java",
        "-jar" => "../Release/SequenceMatrix.jar",
        @_
    );
}

exec_sequence_matrix(
    "--add" => "files/Diptera COI.fasta", 
    "--add" => "files/coi_final.txt"
);

pass("Added two fasta files.");
